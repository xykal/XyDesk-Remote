import test from 'node:test';
import assert from 'node:assert/strict';
import {cleanupOwnedProbe, OWN_PROBE} from './chat-owned-probe-cleanup.mjs';
function fixture(row) {
 const data=new Map(row ? [[`msg:${OWN_PROBE.id}`,row],['msg:owner-message',{text:'must survive'}]] : []);
 const storage={get:async k=>data.get(k),delete:async k=>data.delete(k),transaction:async fn=>fn(storage)};
 return {data,storage};
}
test('cleanup removes only this agent exact unowned probe and leaves owner data intact',async()=>{
 const {data,storage}=fixture({...OWN_PROBE,author_id:null});
 assert.equal(await cleanupOwnedProbe(storage),true); assert.equal(data.size,1); assert.ok(data.has('msg:owner-message'));
 assert.equal(await cleanupOwnedProbe(storage),false);
});
test('cleanup cannot remove changed, attributed or edited rows even with matching ID',async()=>{
 for(const patch of [{text:'owner edit'},{author_id:42},{revision:1},{at:1},{client_msg_id:'other'},{attachment_data:'owner media'},{e2ee_cipher:'owner envelope'}]) {
  const {data,storage}=fixture({...OWN_PROBE,...patch}); assert.equal(await cleanupOwnedProbe(storage),false);assert.equal(data.size,2);
 }
});
test('cleanup rechecks identity inside transaction before deleting',async()=>{
 const {data,storage}=fixture({...OWN_PROBE});
 storage.transaction=async fn=>{data.set(`msg:${OWN_PROBE.id}`,{...OWN_PROBE,revision:1});return fn(storage);};
 assert.equal(await cleanupOwnedProbe(storage),false);assert.equal(data.size,2);
});
