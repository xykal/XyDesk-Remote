/** Ownership is a provider subject, never a nickname/client-supplied github_login. */
export function applyChatMutation(message, principal, body, action, now) {
  if (!message) return { status: 404, code: "not_found" };
  if (!Number.isSafeInteger(message.author_id) || message.author_id !== principal.id)
    return { status: 403, code: "not_owner_or_legacy_message" };
  if (typeof body.operation_id !== "string" || !/^[a-zA-Z0-9-]{16,80}$/.test(body.operation_id))
    return { status: 400, code: "invalid_operation" };
  if (message.last_mutation_id === body.operation_id) {
    if (message.last_mutation_action !== action || action === "edit" && message.text !== String(body.text || "").trim())
      return { status: 409, code: "operation_reused" };
    return { status: 200, message };
  }
  const revision = Number(message.revision || 0);
  if (!Number.isSafeInteger(body.expected_revision) || body.expected_revision !== revision)
    return { status: 409, code: "revision_conflict" };
  if (message.deleted) return { status: 409, code: "message_deleted" };
  const metadata = {
    id: message.id, name: message.name, github_login: message.github_login,
    author_id: message.author_id, at: message.at, country: message.country,
    flag: message.flag, client_msg_id: message.client_msg_id || "",
    revision: revision + 1, last_mutation_id: body.operation_id, last_mutation_action: action,
  };
  if (action === "delete") return { status: 200, message: {
    ...metadata, deleted: true, deleted_at: now, text: "Pesan ini dihapus",
    edited_at: 0, translated: "", delivery_status: "delivered",
  } };
  if (message.attachment_type || message.attachment_kind || message.attachment_data)
    return { status: 400, code: "text_messages_only" };
  if (typeof body.text !== "string" || !body.text.trim() || body.text.trim().length > 500)
    return { status: 400, code: "invalid_text" };
  return { status: 200, message: {
    ...message, ...metadata, text: body.text.trim(), translated: "", edited_at: now,
    e2ee_cipher: "", e2ee_iv: "", e2ee_sig: "", e2ee_verified: false,
  } };
}
