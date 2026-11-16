import { createRoute } from "honox/factory";

export default createRoute(async (c, next) => {
  c.set("requestId", "req-1");
  await next();
});
