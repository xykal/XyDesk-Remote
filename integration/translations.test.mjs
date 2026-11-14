import {test} from 'node:test';
import assert from 'node:assert/strict';
import {languages, englishStrings, translatedStrings} from '../web-next/app/translations.js';

test('20 selectable languages and translation requests obey Worker target and batch contract', async()=>{
 assert.equal(languages.length,20);assert.equal(new Set(languages.map(([id])=>id)).size,20);
 const seen=[];globalThis.localStorage={getItem:()=>null,setItem:()=>{}};
 globalThis.fetch=async (_url,request)=>{const b=JSON.parse(request.body);seen.push(b);return Response.json({status:'ok',items:b.items.map(x=>'translated '+x)});};
 const result=await translatedStrings('zh',new AbortController().signal);
 assert.ok(seen.length>=1);for(const b of seen){assert.equal(b.target,'zh-CN');assert.ok(b.items.length<=40)}
 assert.equal(Object.keys(result).length,englishStrings.length);
});

test('partial provider responses are not saved as complete translations', async()=>{
 let cached=false;globalThis.localStorage={getItem:()=>null,setItem:()=>{cached=true}};
 globalThis.fetch=async (_url,request)=>{const b=JSON.parse(request.body);return Response.json({status:'ok',items:b.items.map(()=> '')});};
 const result=await translatedStrings('fr',new AbortController().signal);assert.equal(Object.keys(result).length,0);assert.equal(cached,false);
});
