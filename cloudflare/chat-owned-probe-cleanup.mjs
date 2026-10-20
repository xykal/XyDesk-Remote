// One-time remediation of THIS agent's unintended anonymous rollout probe.
// Not a moderation API and never a filter/reset of owner messages.
export const OWN_PROBE = Object.freeze({
  id:'0mvg90rg2-vtvpxu', client_msg_id:'local-f9a41f82-e73f-4472-8802-121e45b610f1',
  at:1792474473458, text:'anonymous request must never publish',
});
function isOwnProbe(row) {
  return row?.id === OWN_PROBE.id && row.client_msg_id === OWN_PROBE.client_msg_id &&
    row.at === OWN_PROBE.at && row.text === OWN_PROBE.text && row.author_id == null &&
    !row.deleted && !row.revision && !row.attachment_data && !row.e2ee_cipher;
}
export async function cleanupOwnedProbe(storage) {
  const key=`msg:${OWN_PROBE.id}`;
  if (!isOwnProbe(await storage.get(key))) return false;
  return storage.transaction(async tx => {
    if (!isOwnProbe(await tx.get(key))) return false;
    await tx.delete(key); return true;
  });
}
