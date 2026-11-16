import { createClient } from "honox/client";
import { createElement, type ElementType, type ReactElement } from "react";
import { hydrateRoot } from "react-dom/client";

/** hx4s.react.IslandExport が export に付ける props の検証関数。不正なら理由を返す */
type ValidatedComponent = { hx4sValidate?: (props: unknown) => string | undefined };

void createClient<ReactElement>({
  hydrate: (element, root) => {
    // HTML と client の世代がずれて props が合わないときは hydrate せず、SSR の markup を残す
    const problem = (element.type as ValidatedComponent).hx4sValidate?.(element.props);
    if (problem !== undefined) {
      console.error(`[hx4s] skipped hydration of ${root.getAttribute("component-name")}: ${problem}`);
      return;
    }
    hydrateRoot(root, element);
  },
  createElement: (type: ElementType, props: Record<string, unknown>) => createElement(type, props),
}).then(() => document.documentElement.setAttribute("data-hx4s-ready", ""));
