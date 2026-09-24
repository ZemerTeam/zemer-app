import { getCred, describeCred } from "./cred.mjs";
import { createMinter } from "./potoken.mjs";
import { createCipher } from "./cipher.mjs";
import crypto from "node:crypto";
const VIDEO_ID = process.argv[2] || "gl9VXSMZwTo";
const PO = process.argv[3] || "video"; // which pot goes in streamerContext: video|web|both-url
const PLAYER_ORIGIN = "https://music.youtube.com";
const C = { clientName: "MWEB", clientVersion: "2.20260708.05.00", clientId: 2, ua: "Mozilla/5.0 (iPad; CPU OS 16_7_10 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1,gzip(gfe)" };
const dec = (s) => { try { return s && /%[0-9A-Fa-f]{2}/.test(s) ? decodeURIComponent(s) : s; } catch { return s; } };
const varint = (n) => { const o = []; let v = BigInt(n); do { let b = Number(v & 0x7fn); v >>= 7n; if (v) b |= 0x80; o.push(b); } while (v); return Buffer.from(o); };
const tag = (f, w) => varint((f << 3) | w); const fV = (f, v) => Buffer.concat([tag(f,0),varint(v)]); const fB=(f,b)=>Buffer.concat([tag(f,2),varint(b.length),b]); const fS=(f,s)=>fB(f,Buffer.from(s,"utf8")); const EMPTY=Buffer.alloc(0);
const fmtId=(i,lm)=>Buffer.concat([fV(1,i),lm?fV(2,BigInt(lm)):EMPTY]);
const clientInfo=(c)=>Buffer.concat([fV(16,c.clientId),fS(17,c.clientVersion)]);
const streamerCtx=(c,pot,cookie,ctx)=>Buffer.concat([fB(1,clientInfo(c)),pot?fB(2,pot):EMPTY,cookie?fB(3,cookie):EMPTY,...(ctx||[]).map(u=>fB(5,u))]);
const bufRange=(f,e,s)=>Buffer.concat([fB(1,fmtId(f.itag,f.lastModified)),fV(2,0),fV(3,Math.round(e)),fV(4,1),fV(5,s)]);
const buildReq=(ust,fmt,pot,c,pt,ranges,cookie,sel,ctx)=>Buffer.concat([fB(1,Buffer.concat([fV(28,Math.round(pt)),fV(40,1)])),sel?fB(2,fmtId(fmt.itag,fmt.lastModified)):EMPTY,...ranges.map(r=>fB(3,bufRange(fmt,r.e,r.s))),pt?fV(4,Math.round(pt)):EMPTY,fB(5,ust),fB(16,fmtId(fmt.itag,fmt.lastModified)),fB(19,streamerCtx(c,pot,cookie,ctx))]);
function umpVar(b,p){const b0=b[p];let sz=1;if(b0>=128)sz=2;if(b0>=192)sz=3;if(b0>=224)sz=4;if(b0>=240)sz=5;let v;if(sz===1)v=b0;else if(sz===2)v=(b0&0x3f)+b[p+1]*64;else if(sz===3)v=(b0&0x1f)+b[p+1]*32+b[p+2]*8192;else if(sz===4)v=(b0&0x0f)+b[p+1]*16+b[p+2]*4096+b[p+3]*1048576;else v=b[p+1]+b[p+2]*256+b[p+3]*65536+b[p+4]*16777216;return[v,sz];}
function parseUmp(b){const P=[];let p=0;while(p<b.length){const[t,ts]=umpVar(b,p);p+=ts;if(p>=b.length)break;const[sz,ss]=umpVar(b,p);p+=ss;P.push({type:t,payload:b.subarray(p,p+sz)});p+=sz;}return P;}
function pbv(b,pos){let sh=0n,r=0n,p=pos;for(;;){const x=b[p++];r|=BigInt(x&0x7f)<<sh;if(!(x&0x80))break;sh+=7n;}return[r,p-pos];}
function readProto(b){const o={};let p=0;while(p<b.length){const[t,ts]=pbv(b,p);p+=ts;const tn=Number(t),f=tn>>3,w=tn&7;let val;if(w===0){const[v,vs]=pbv(b,p);p+=vs;val=v;}else if(w===2){const[l,ls]=pbv(b,p);p+=ls;const ln=Number(l);val=b.subarray(p,p+ln);p+=ln;}else if(w===5){val=BigInt(b.readUInt32LE(p));p+=4;}else if(w===1){val=b.readBigUInt64LE(p);p+=8;}else break;(o[f]||=[]).push(val);}return o;}
const N=(x)=>x==null?0:Number(x);
function trMs(b){if(!b)return{s:0,d:0};const t=readProto(b);const ts=N(t[3]?.[0])||1000;return{s:N(t[1]?.[0])/ts*1000,d:N(t[2]?.[0])/ts*1000};}
function sapisidHash(cookie){const m=cookie.match(/(?:^|; )SAPISID=([^;]+)/);if(!m)return null;const ts=Math.floor(Date.now()/1000);return `SAPISIDHASH ${ts}_${crypto.createHash("sha1").update(`${ts} ${m[1]} ${PLAYER_ORIGIN}`).digest("hex")}`;}
const withPot=(u,p)=>p?u+(u.includes("?")?"&":"?")+"pot="+encodeURIComponent(p):u;
(async()=>{
  const cred=await getCred(); const vd=dec(cred.visitorData);
  const cipher=await createCipher({}); const minter=await createMinter(vd);
  const webPot=await minter.mint(vd); const videoPot=await minter.mint(VIDEO_ID);
  const webBytes=Buffer.from(webPot.replace(/-/g,"+").replace(/_/g,"/"),"base64");
  const videoBytes=Buffer.from(videoPot.replace(/-/g,"+").replace(/_/g,"/"),"base64");
  const body={context:{client:{clientName:C.clientName,clientVersion:C.clientVersion,hl:"en",gl:"US",visitorData:vd}},videoId:VIDEO_ID,contentCheckOk:true,racyCheckOk:true};
  if(cred.dataSyncId)body.context.user={onBehalfOfUser:cred.dataSyncId};
  body.playbackContext={contentPlaybackContext:{signatureTimestamp:Number(cipher.sts)}};
  body.serviceIntegrityDimensions={poToken:webPot};
  const h={"Content-Type":"application/json","X-YouTube-Client-Name":String(C.clientId),"X-YouTube-Client-Version":C.clientVersion,"X-Origin":PLAYER_ORIGIN,Referer:PLAYER_ORIGIN+"/","User-Agent":C.ua,"X-Goog-Visitor-Id":vd};
  if(cred.cookie){h.cookie=cred.cookie;const a=sapisidHash(cred.cookie);if(a)h.Authorization=a;}
  const res=await fetch(PLAYER_ORIGIN+"/youtubei/v1/player?prettyPrint=false",{method:"POST",headers:h,body:JSON.stringify(body)});
  const j=JSON.parse(await res.text()); const sd=j.streamingData||{};
  const ustB64=j?.playerConfig?.mediaCommonConfig?.mediaUstreamerRequestConfig?.videoPlaybackUstreamerConfig;
  const fmt=(sd.adaptiveFormats||[]).filter(f=>f.width==null&&(!f.audioTrack||f.audioTrack.isAutoDubbed==null)).sort((a,b)=>b.bitrate-a.bitrate)[0];
  if(j?.playabilityStatus?.status!=="OK"||!sd.serverAbrStreamingUrl||!fmt){console.log("not usable:",j?.playabilityStatus?.status);return;}
  const ctxPot = PO==="web"?webBytes:videoBytes;           // streamerContext.poToken
  const urlPotStr = PO==="both-url"?webPot:videoPot;        // &pot=
  console.log(`MODE=${PO}: streamerContext.poToken=${PO==="web"?"web/session":"video/content"}, urlPot=${urlPotStr===webPot?"web":"video"}`);
  const ust=Buffer.from(ustB64,"base64"); const xform=(u)=>cipher.transformNParamInUrl(u);
  let url=withPot(xform(sd.serverAbrStreamingUrl),urlPotStr),pt=0,be=0,ls=0,es=0,cookie=null,iter=0,dry=0;const ctx=new Map();const segs=new Map();let ib=0;const clen=Number(fmt.contentLength);
  while(iter<25&&dry<6){iter++;const ranges=ls?[{e:be,s:ls}]:[];
    const r=await fetch(url,{method:"POST",headers:{"User-Agent":C.ua,"Content-Type":"application/x-protobuf"},body:buildReq(ust,fmt,ctxPot,C,pt,ranges,cookie,ls>0,[...ctx.values()])});
    if(r.status!==200){console.log(`iter ${iter} HTTP ${r.status}`);break;}
    const parts=parseUmp(Buffer.from(await r.arrayBuffer()));const hdr={};let ns=0,prot=null;
    for(const p of parts){if(p.type===20){const hh=readProto(p.payload);const id=N(hh[1]?.[0]);const t=trMs(hh[15]?.[0]);hdr[id]={seq:N(hh[9]?.[0]),init:!!N(hh[8]?.[0]),clen:N(hh[14]?.[0]),s:t.s,d:t.d};}else if(p.type===42)es=N(readProto(p.payload)[4]?.[0])||es;else if(p.type===35){const u=readProto(p.payload);if(u[7]?.[0])cookie=u[7][0];}else if(p.type===57){const u=readProto(p.payload);ctx.set(N(u[1]?.[0]),Buffer.concat([fV(1,N(u[1]?.[0])),fB(2,u[3]?.[0]||EMPTY)]));}else if(p.type===58)prot=N(readProto(p.payload)[1]?.[0]);else if(p.type===43){const[len,s]=umpVar(p.payload,1);url=withPot(xform(p.payload.subarray(1+s,1+s+len).toString("utf8")),urlPotStr);}}
    for(const id in hdr){const hh=hdr[id];if(hh.init){if(!ib)ib=hh.clen;continue;}if(!segs.has(hh.seq)){segs.set(hh.seq,hh.clen);ns++;}if(hh.seq>ls)ls=hh.seq;const e=hh.s+hh.d;if(e>be)be=e;}
    pt=be;dry=ns>0?0:dry+1;const sum=[...segs.values()].reduce((a,b)=>a+b,0);
    if(iter<=4||ns>0)console.log(`  iter ${iter}: newSeg=${ns} seq=${ls}/${es} ${Math.round((ib+sum)/clen*100)}% PROT=${prot}`);
    if(es&&ls>=es){console.log(`  >>> WHOLE ✓ (${segs.size}/${es})`);break;}
  }
  const sum=[...segs.values()].reduce((a,b)=>a+b,0);
  console.log(`  final: ${segs.size}/${es} ${Math.round((ib+sum)/clen*100)}%\n`);
})();
