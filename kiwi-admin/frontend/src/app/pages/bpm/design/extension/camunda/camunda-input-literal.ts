/**
 * Camunda 将 camunda:inputParameter 的 body 当作 JUEL 解析。
 * 若需整段视为纯字符串（不做变量插值），应写成 EL 字符串字面量：${'...'}。
 */

const LITERAL_PREFIX = "${'";
const LITERAL_SUFFIX = "'}";

/**
 * 编码为 Camunda 可持久化的字面量表达式（引擎求值结果等于原始字符串）。
 */
export function encodeCamundaInputLiteral(raw: string): string {
  if (raw === '') {
    return "${''}";
  }
  const escaped = raw.replace(/\\/g, '\\\\').replace(/'/g, "\\'");
  return LITERAL_PREFIX + escaped + LITERAL_SUFFIX;
}

/**
 * 将存储值还原为原始字符串；若非 ${'...'} 形式则原样返回（兼容旧 BPMN）。
 */
export function decodeCamundaInputLiteral(stored: string): string {
  if (!stored.startsWith(LITERAL_PREFIX) || !stored.endsWith(LITERAL_SUFFIX)) {
    return stored;
  }
  const inner = stored.slice(LITERAL_PREFIX.length, stored.length - LITERAL_SUFFIX.length);
  let out = '';
  for (let i = 0; i < inner.length; i++) {
    const c = inner[i];
    if (c === '\\' && i + 1 < inner.length) {
      const n = inner[i + 1];
      if (n === "'" || n === '\\') {
        out += n === "'" ? "'" : '\\';
        i++;
        continue;
      }
    }
    out += c;
  }
  return out;
}
