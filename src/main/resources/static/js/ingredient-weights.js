(function () {
    const list = document.getElementById('ing-list');
    if (!list) return;

    const search = document.getElementById('ing-search');
    const onlyMissing = document.getElementById('only-missing');
    const count = document.getElementById('ing-count');
    const items = Array.from(list.querySelectorAll('.ing-item'));

    function filter() {
        const query = search.value.trim().toLowerCase();
        // The ingredient just saved stays on screen, even when it is complete now.
        const justSaved = location.hash.slice(1);
        let shown = 0;
        for (const item of items) {
            const matches = (!query || item.dataset.name.includes(query))
                && (!onlyMissing.checked || item.dataset.incomplete === 'true');
            const visible = matches || item.id === justSaved;
            item.hidden = !visible;
            if (visible) shown++;
        }
        const todo = items.filter(item => item.dataset.incomplete === 'true').length;
        count.textContent = shown + ' of ' + items.length + ' ingredients · ' + todo + ' still need information';
    }

    // Saving sends the search and the tick box with the form, so the page comes back the same way.
    list.querySelectorAll('.ing-body').forEach(form => form.addEventListener('submit', () => {
        form.elements.q.value = search.value.trim();
        form.elements.missing.value = onlyMissing.checked ? 'true' : '';
    }));

    search.addEventListener('input', filter);
    onlyMissing.addEventListener('change', filter);
    filter();

    // After saving, the page reloads with #ingredient-<id>. Open that one and scroll to it.
    if (location.hash.startsWith('#ingredient-')) {
        const target = document.getElementById(location.hash.slice(1));
        if (target) {
            target.open = true;
            target.scrollIntoView();
        }
    }
})();
