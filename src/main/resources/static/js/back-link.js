// "← Back" on a recipe goes to the page the cook came from: the planner, a search, the recipe list.
// When there is no earlier page in this site (the recipe was opened from a link elsewhere or directly),
// it goes to the full recipe list.
(function () {
    const link = document.querySelector('[data-back-link]');
    if (!link || !document.referrer) return;

    const from = new URL(document.referrer);
    const cameFromThisSite = from.origin === location.origin && from.pathname !== '/login';
    if (!cameFromThisSite) return;

    link.addEventListener('click', event => {
        if (history.length > 1) {
            event.preventDefault();
            history.back();
        }
    });
})();
