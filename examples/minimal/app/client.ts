import { createClient } from "honox/client";
import { islandPropsProblem } from "hx4s-vite/client";
import { createElement, type ElementType, type ReactElement } from "react";
import { hydrateRoot } from "react-dom/client";

void createClient<ReactElement>({
  hydrate: (element, root) => {
    // When the HTML and the client are from different builds and the props don't match, skip hydration and keep the SSR markup (islands inside templates are checked too)
    const problem = islandPropsProblem(element);
    if (problem !== undefined) {
      console.error(`[hx4s] skipped hydration of ${root.getAttribute("component-name")}: ${problem}`);
      return;
    }
    hydrateRoot(root, element);
  },
  createElement: (type: ElementType, props: Record<string, unknown>) => createElement(type, props),
}).then(() => document.documentElement.setAttribute("data-hx4s-ready", ""));
