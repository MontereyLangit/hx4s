/** createClient の hydrate に渡される要素の形。React の ReactElement はこれに当てはまる */
export interface IslandElement {
  readonly type: unknown;
  readonly props: unknown;
}

type Validate = (props: unknown) => string | undefined;

function isElement(value: unknown): value is IslandElement {
  return typeof value === "object" && value !== null && "$$typeof" in value && "type" in value && "props" in value;
}

function validatorOf(type: unknown): Validate | undefined {
  if ((typeof type !== "function" && typeof type !== "object") || type === null) return undefined;
  const validate: unknown = (type as { hx4sValidate?: unknown }).hx4sValidate;
  return typeof validate === "function" ? (validate as Validate) : undefined;
}

function problemIn(element: IslandElement, path: string): string | undefined {
  const problem = validatorOf(element.type)?.(element.props);
  if (problem !== undefined) return path === "" ? problem : `${path}: ${problem}`;
  if (typeof element.props !== "object" || element.props === null) return undefined;
  for (const [key, value] of Object.entries(element.props)) {
    const found = problemInValue(value, path === "" ? `props.${key}` : `${path}.props.${key}`);
    if (found !== undefined) return found;
  }
  return undefined;
}

function problemInValue(value: unknown, path: string): string | undefined {
  if (isElement(value)) return problemIn(value, path);
  if (!Array.isArray(value)) return undefined;
  for (const [index, item] of value.entries()) {
    const found = problemInValue(item, `${path}[${index}]`);
    if (found !== undefined) return found;
  }
  return undefined;
}

/**
 * hydrate する前に、island と、要素 props（HonoX が template から復元する子）に含まれる island の props を確かめる。
 * hx4s.react.IslandExport が付けた検証関数が理由を返したら、その最初のものを返す。問題が無ければ undefined。
 */
export function islandPropsProblem(element: IslandElement): string | undefined {
  return problemIn(element, "");
}
