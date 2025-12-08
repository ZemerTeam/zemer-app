#include <jni.h>
#include <android/log.h>
#include <string>
#include <cstring>
#include <vector>

#include "Ap4.h"
#include "Ap4MetaData.h"

#define LOG_TAG "CoverArtNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Helper to add text metadata atom (©nam, ©ART, ©alb, etc.)
static void addTextMetadata(AP4_ContainerAtom* ilst, AP4_Atom::Type type, const char* text) {
    if (!text || strlen(text) == 0) return;

    // Remove existing atom of this type
    ilst->DeleteChild(type);

    // Create container -> data structure
    AP4_ContainerAtom* container = new AP4_ContainerAtom(type);
    AP4_StringMetaDataValue value(text);  // Constructor sets TYPE_STRING_UTF_8 automatically
    AP4_DataAtom* dataAtom = new AP4_DataAtom(value);
    container->AddChild(dataAtom);
    ilst->AddChild(container);
}

extern "C" {

/**
 * Embed metadata (cover art, title, artist, album, year) into an M4A/MP4 file.
 * Uses a two-pass approach: first parse to build modified moov, then rebuild file.
 * All text is stored as UTF-8 (supports Hebrew, Arabic, and all Unicode).
 */
JNIEXPORT jboolean JNICALL
Java_com_jtech_zemer_utils_CoverArtNative_embedMetadata(
        JNIEnv *env,
        jclass clazz,
        jstring inputPath,
        jstring outputPath,
        jbyteArray artworkData,
        jstring titleStr,
        jstring artistStr,
        jstring albumStr,
        jstring yearStr) {

    const char *input = env->GetStringUTFChars(inputPath, nullptr);
    const char *output = env->GetStringUTFChars(outputPath, nullptr);

    // Artwork can be null
    jbyte *artwork = nullptr;
    jsize artworkSize = 0;
    if (artworkData != nullptr) {
        artwork = env->GetByteArrayElements(artworkData, nullptr);
        artworkSize = env->GetArrayLength(artworkData);
    }

    // Get text metadata (UTF-8 for Hebrew/Unicode support)
    const char *title = titleStr ? env->GetStringUTFChars(titleStr, nullptr) : nullptr;
    const char *artist = artistStr ? env->GetStringUTFChars(artistStr, nullptr) : nullptr;
    const char *album = albumStr ? env->GetStringUTFChars(albumStr, nullptr) : nullptr;
    const char *year = yearStr ? env->GetStringUTFChars(yearStr, nullptr) : nullptr;

    LOGI("Embedding metadata: %s -> %s (%d bytes artwork)", input, output, artworkSize);
    if (title) LOGI("  Title: %s", title);
    if (artist) LOGI("  Artist: %s", artist);
    if (album) LOGI("  Album: %s", album);
    if (year) LOGI("  Year: %s", year);

    bool success = false;

    do {
        // First pass: find atom positions in the file
        AP4_ByteStream *inputStream = nullptr;
        AP4_Result result = AP4_FileByteStream::Create(input, AP4_FileByteStream::STREAM_MODE_READ, inputStream);
        if (AP4_FAILED(result) || !inputStream) {
            LOGE("Failed to open input file: %d", result);
            break;
        }

        // Get file size
        AP4_LargeSize fileSize = 0;
        inputStream->GetSize(fileSize);
        LOGI("Input file size: %lld", (long long)fileSize);

        // Scan for all atom positions (DASH files have multiple moof+mdat pairs)
        struct AtomInfo {
            AP4_UI32 type;
            AP4_Position pos;
            AP4_UI64 size;
        };
        std::vector<AtomInfo> atoms;
        AP4_Position moovPos = 0;
        AP4_UI64 moovSize = 0;

        AP4_Position pos = 0;
        while (pos < (AP4_Position)fileSize) {
            inputStream->Seek(pos);
            AP4_UI32 atomSizeSmall;
            AP4_UI32 atomType;
            if (AP4_FAILED(inputStream->ReadUI32(atomSizeSmall))) break;
            if (AP4_FAILED(inputStream->ReadUI32(atomType))) break;

            AP4_UI64 atomSize = atomSizeSmall;
            if (atomSizeSmall == 1) {
                // 64-bit size
                if (AP4_FAILED(inputStream->ReadUI64(atomSize))) break;
            } else if (atomSizeSmall == 0) {
                atomSize = fileSize - pos;
            }

            atoms.push_back({atomType, pos, atomSize});

            if (atomType == AP4_ATOM_TYPE_MOOV) {
                moovPos = pos;
                moovSize = atomSize;
            }

            pos += atomSize;
        }

        LOGI("Found %zu atoms in file", atoms.size());

        if (moovSize == 0) {
            LOGE("Missing moov atom");
            inputStream->Release();
            break;
        }

        // Parse the file to get moov structure
        inputStream->Seek(0);
        AP4_File file(*inputStream, true);
        AP4_Movie *movie = file.GetMovie();

        if (!movie) {
            LOGE("No movie found in file");
            inputStream->Release();
            break;
        }

        AP4_MoovAtom *moov = movie->GetMoovAtom();
        if (!moov) {
            LOGE("No moov atom found");
            inputStream->Release();
            break;
        }

        // Build the metadata structure
        AP4_ContainerAtom *udta = AP4_DYNAMIC_CAST(AP4_ContainerAtom, moov->FindChild("udta"));
        if (!udta) {
            udta = new AP4_ContainerAtom(AP4_ATOM_TYPE_UDTA);
            moov->AddChild(udta);
            LOGI("Created udta atom");
        }

        AP4_ContainerAtom *meta = AP4_DYNAMIC_CAST(AP4_ContainerAtom, udta->FindChild("meta"));
        if (!meta) {
            meta = new AP4_ContainerAtom(AP4_ATOM_TYPE_META, (AP4_UI32)0, (AP4_UI32)0);
            AP4_HdlrAtom *hdlr = new AP4_HdlrAtom(AP4_HANDLER_TYPE_MDIR, "");
            meta->AddChild(hdlr);
            udta->AddChild(meta);
            LOGI("Created meta atom with hdlr");
        }

        AP4_ContainerAtom *ilst = AP4_DYNAMIC_CAST(AP4_ContainerAtom, meta->FindChild("ilst"));
        if (!ilst) {
            ilst = new AP4_ContainerAtom(AP4_ATOM_TYPE_ILST);
            meta->AddChild(ilst);
            LOGI("Created ilst atom");
        }

        // Add text metadata (©nam = title, ©ART = artist, ©alb = album, ©day = year)
        addTextMetadata(ilst, AP4_ATOM_TYPE_cNAM, title);   // Title
        addTextMetadata(ilst, AP4_ATOM_TYPE_cART, artist);  // Artist
        addTextMetadata(ilst, AP4_ATOM_TYPE_cALB, album);   // Album
        addTextMetadata(ilst, AP4_ATOM_TYPE_cDAY, year);    // Year
        LOGI("Added text metadata atoms");

        // Add cover art if provided
        if (artwork != nullptr && artworkSize > 0) {
            // Remove existing cover art
            ilst->DeleteChild(AP4_ATOM_TYPE_COVR);

            // Detect image type
            AP4_MetaData::Value::Type valueType = AP4_MetaData::Value::TYPE_JPEG;
            if (artworkSize >= 8 &&
                (unsigned char)artwork[0] == 0x89 &&
                (unsigned char)artwork[1] == 0x50 &&
                (unsigned char)artwork[2] == 0x4E &&
                (unsigned char)artwork[3] == 0x47) {
                valueType = AP4_MetaData::Value::TYPE_GIF;
                LOGI("Detected PNG artwork");
            } else {
                LOGI("Using JPEG artwork type");
            }

            AP4_ContainerAtom *covr = new AP4_ContainerAtom(AP4_ATOM_TYPE_COVR);
            AP4_BinaryMetaDataValue value(valueType, (const AP4_UI08*)artwork, artworkSize);
            AP4_DataAtom *dataAtom = new AP4_DataAtom(value);
            covr->AddChild(dataAtom);
            ilst->AddChild(covr);
            LOGI("Added cover art atom");
        }

        // Open output file
        AP4_ByteStream *outputStream = nullptr;
        result = AP4_FileByteStream::Create(output, AP4_FileByteStream::STREAM_MODE_WRITE, outputStream);
        if (AP4_FAILED(result) || !outputStream) {
            LOGE("Failed to create output file: %d", result);
            inputStream->Release();
            break;
        }

        // Write all atoms in order, replacing moov with our modified version
        const AP4_Size bufferSize = 65536;
        AP4_UI08 *buffer = new AP4_UI08[bufferSize];
        AP4_UI64 totalWritten = 0;

        for (const auto &atom : atoms) {
            if (atom.type == AP4_ATOM_TYPE_MOOV) {
                // Write modified moov
                AP4_UI64 newMoovSize = moov->GetSize();
                moov->Write(*outputStream);
                totalWritten += newMoovSize;
                LOGI("Wrote moov: %lld bytes (was %lld)", (long long)newMoovSize, (long long)atom.size);
            } else {
                // Copy atom raw bytes
                inputStream->Seek(atom.pos);
                AP4_UI64 remaining = atom.size;
                while (remaining > 0) {
                    AP4_Size toRead = (remaining > bufferSize) ? bufferSize : (AP4_Size)remaining;
                    result = inputStream->Read(buffer, toRead);
                    if (AP4_FAILED(result)) {
                        LOGE("Failed to read atom: %d", result);
                        break;
                    }
                    outputStream->Write(buffer, toRead);
                    remaining -= toRead;
                }
                totalWritten += atom.size;
            }
        }

        delete[] buffer;
        LOGI("Total written: %lld bytes", (long long)totalWritten);

        outputStream->Release();
        inputStream->Release();

        success = true;
        LOGI("Cover art embedded successfully");

    } while (false);

    env->ReleaseStringUTFChars(inputPath, input);
    env->ReleaseStringUTFChars(outputPath, output);
    if (artwork) env->ReleaseByteArrayElements(artworkData, artwork, JNI_ABORT);
    if (title) env->ReleaseStringUTFChars(titleStr, title);
    if (artist) env->ReleaseStringUTFChars(artistStr, artist);
    if (album) env->ReleaseStringUTFChars(albumStr, album);
    if (year) env->ReleaseStringUTFChars(yearStr, year);

    return success ? JNI_TRUE : JNI_FALSE;
}

/**
 * Defragment a DASH/fragmented MP4 file to standard MP4.
 * This is needed because DASH files use moof/mdat structure instead of moov/mdat.
 */
JNIEXPORT jboolean JNICALL
Java_com_jtech_zemer_utils_CoverArtNative_defragmentFile(
        JNIEnv *env,
        jclass clazz,
        jstring inputPath,
        jstring outputPath) {

    const char *input = env->GetStringUTFChars(inputPath, nullptr);
    const char *output = env->GetStringUTFChars(outputPath, nullptr);

    LOGI("Defragmenting: %s -> %s", input, output);

    bool success = false;

    do {
        // Open input file
        AP4_ByteStream *inputStream = nullptr;
        AP4_Result result = AP4_FileByteStream::Create(input, AP4_FileByteStream::STREAM_MODE_READ, inputStream);
        if (AP4_FAILED(result) || !inputStream) {
            LOGE("Failed to open input file for defrag: %d", result);
            break;
        }

        // Open output stream
        AP4_ByteStream *outputStream = nullptr;
        result = AP4_FileByteStream::Create(output, AP4_FileByteStream::STREAM_MODE_WRITE, outputStream);
        if (AP4_FAILED(result) || !outputStream) {
            LOGE("Failed to create output file for defrag: %d", result);
            inputStream->Release();
            break;
        }

        // Parse the file
        AP4_File file(*inputStream, true);

        if (file.GetMovie()) {
            // File has moov, just copy it
            inputStream->Seek(0);
            AP4_LargeSize fileSize = 0;
            inputStream->GetSize(fileSize);

            const AP4_Size bufferSize = 4096;
            AP4_Byte buffer[bufferSize];
            while (fileSize > 0) {
                AP4_Size toRead = (fileSize > bufferSize) ? bufferSize : (AP4_Size)fileSize;
                result = inputStream->Read(buffer, toRead);
                if (AP4_FAILED(result)) break;
                outputStream->Write(buffer, toRead);
                fileSize -= toRead;
            }
            success = true;
            LOGI("File copied (already defragmented)");
        } else {
            LOGE("File has no movie atom, cannot defragment in this version");
            // For truly fragmented files, we'd need more complex reconstruction
            // This basic version handles files that just need metadata addition
        }

        outputStream->Release();
        inputStream->Release();

    } while (false);

    env->ReleaseStringUTFChars(inputPath, input);
    env->ReleaseStringUTFChars(outputPath, output);

    return success ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
