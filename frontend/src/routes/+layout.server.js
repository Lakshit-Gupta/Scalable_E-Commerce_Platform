/**
 * Root layout load (v0.1.0): expose the (display-only) session user to every page so the header can
 * show login/logout and the cart link. Never send the raw token to the browser.
 */
export function load({ locals }) {
  return { user: locals.user ? { username: locals.user.username } : null };
}
