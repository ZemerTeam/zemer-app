#ifndef ZEMER_CODE_CHECK_H
#define ZEMER_CODE_CHECK_H

// Whether the executable segment holding [addr] differs in memory from the bytes of the file it is mapped from
// (an inline hook, a patched instruction, a software breakpoint). 1 = differs, 0 = identical, -1 = not checkable.
int zemer_code_patched_at(const void *addr);

#endif
