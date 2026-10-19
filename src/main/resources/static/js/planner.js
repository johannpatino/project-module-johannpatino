(function () {
    // Printing. Just before the print dialog opens, the page gets the "printing" class (single-line
    // rows, no buttons). If the page is still longer than one sheet, it is shrunk to fit.
    const PRINT_HEIGHT_PX = 960; // A4 and Letter, minus the 12 mm page margins, with some room to spare
    const printable = document.querySelector('[data-printable]');

    document.querySelectorAll('[data-print]').forEach(button => {
        button.addEventListener('click', () => window.print());
    });

    window.addEventListener('beforeprint', () => {
        if (!printable) return;
        document.body.classList.add('printing');
        printable.style.zoom = '';
        const height = printable.getBoundingClientRect().height;
        if (height > PRINT_HEIGHT_PX) printable.style.zoom = String(PRINT_HEIGHT_PX / height);
    });

    window.addEventListener('afterprint', () => {
        if (!printable) return;
        document.body.classList.remove('printing');
        printable.style.zoom = '';
    });

    // Dragging planner cards. Grab a card by its grab bar. Works with mouse and touch.
    const SCROLL_KEY = 'plannerScroll';
    const EDGE_PX = 90;         // within this distance of the top or bottom of the window, the page scrolls
    const SCROLL_STEP_PX = 14;  // how far each step scrolls while a card is held there
    const moveForm = document.getElementById('move-form');
    let drag = null;
    let lastPointer = null;
    let scrollTimer = null;

    if (moveForm) {
        document.addEventListener('pointerdown', event => {
            const grip = event.target.closest('[data-grip]');
            if (!grip || event.button !== 0) return;

            const card = grip.closest('[data-card]');
            const rect = card.getBoundingClientRect();
            const ghost = card.cloneNode(true);
            ghost.classList.add('card-ghost');
            ghost.style.width = rect.width + 'px';
            document.body.appendChild(ghost);
            card.classList.add('is-dragging');

            drag = {
                card,
                ghost,
                pointerId: event.pointerId,
                offsetX: event.clientX - rect.left,
                offsetY: event.clientY - rect.top,
            };
            lastPointer = event;
            grip.setPointerCapture(event.pointerId);
            placeGhost(event);
            startAutoScroll();
            event.preventDefault();
        });

        document.addEventListener('pointermove', event => {
            if (!drag || event.pointerId !== drag.pointerId) return;
            lastPointer = event;
            placeGhost(event);
            highlight(dropZoneAt(event));
        });

        document.addEventListener('pointerup', event => finishDrag(event, true));
        document.addEventListener('pointercancel', event => finishDrag(event, false));
    }

    // While a card is held near the top or bottom of the window, the page scrolls, so cards in other
    // weeks come into reach. It keeps scrolling even when the pointer stops moving.
    function startAutoScroll() {
        scrollTimer = setInterval(() => {
            if (!drag || !lastPointer) return;
            let step = 0;
            if (lastPointer.clientY < EDGE_PX) step = -SCROLL_STEP_PX;
            if (lastPointer.clientY > window.innerHeight - EDGE_PX) step = SCROLL_STEP_PX;
            if (step === 0) return;
            window.scrollBy(0, step);
            highlight(dropZoneAt(lastPointer));
        }, 16);
    }

    function placeGhost(event) {
        drag.ghost.style.left = (event.clientX - drag.offsetX) + 'px';
        drag.ghost.style.top = (event.clientY - drag.offsetY) + 'px';
    }

    // The drop zone under the pointer. The ghost has pointer-events off, so it is never picked.
    function dropZoneAt(event) {
        const element = document.elementFromPoint(event.clientX, event.clientY);
        return element ? element.closest('[data-drop]') : null;
    }

    function highlight(zone) {
        document.querySelectorAll('[data-drop].is-over').forEach(el => el.classList.remove('is-over'));
        if (zone) zone.classList.add('is-over');
    }

    function finishDrag(event, dropped) {
        if (!drag || event.pointerId !== drag.pointerId) return;
        const current = drag;
        const zone = dropped ? dropZoneAt(event) : null;
        drag = null;
        lastPointer = null;
        clearInterval(scrollTimer);

        current.ghost.remove();
        current.card.classList.remove('is-dragging');
        highlight(null);

        if (!zone) return;
        const from = {
            week: current.card.closest('[data-week-id]').dataset.weekId,
            day: current.card.dataset.day || '',
            person: current.card.dataset.person || '',
        };
        const to = {
            week: zone.closest('[data-week-id]').dataset.weekId,
            day: zone.dataset.day || '',
            person: zone.dataset.person || '',
        };
        // Dropped where it already was: nothing to do.
        if (from.week === to.week && from.day === to.day && from.person === to.person) return;
        sendMove(current.card.dataset.entryId, to);
    }

    // Puts the card in its new place on the server, then the page reloads at the same scroll position.
    function sendMove(entryId, to) {
        moveForm.action = '/planner/entries/' + encodeURIComponent(entryId) + '/move';
        moveForm.elements.weekId.value = to.week;
        moveForm.elements.day.value = to.day;
        moveForm.elements.person.value = to.person;
        try {
            sessionStorage.setItem(SCROLL_KEY, String(window.scrollY));
        } catch (e) {
            // Storage can be blocked. The page then just starts at the top.
        }
        moveForm.submit();
    }

    try {
        const saved = sessionStorage.getItem(SCROLL_KEY);
        if (saved !== null) {
            sessionStorage.removeItem(SCROLL_KEY);
            window.scrollTo(0, Number(saved));
        }
    } catch (e) {
        // Storage can be blocked. Nothing to restore.
    }

    // Any button with data-open-dialog opens the <dialog> with that id.
    document.querySelectorAll('[data-open-dialog]').forEach(button => {
        button.addEventListener('click', () => {
            const dialog = document.getElementById(button.dataset.openDialog);
            refreshSubmit(dialog.querySelector('[data-planner-form]'));
            dialog.showModal();
        });
    });

    // Any button with data-close-dialog closes the dialog it sits in.
    document.querySelectorAll('[data-close-dialog]').forEach(button => {
        button.addEventListener('click', () => button.closest('dialog').close());
    });

    // The Nutrition button under a day: the totals are fetched when it is clicked, not on page load.
    document.querySelectorAll('[data-day-nutrition]').forEach(button => {
        button.addEventListener('click', async () => {
            const dialog = document.getElementById('day-nutrition-dialog');
            const body = dialog.querySelector('[data-day-nutrition-body]');
            body.textContent = 'Loading...';
            dialog.showModal();
            try {
                const url = `/planner/weeks/${button.dataset.weekId}/days/${button.dataset.day}/nutrition`;
                const response = await fetch(url, {credentials: 'same-origin'});
                if (!response.ok) throw new Error('HTTP ' + response.status);
                body.innerHTML = await response.text();
            } catch (error) {
                body.textContent = "Couldn't load this day's nutrition. Try again.";
            }
        });
    });

    // "Add to planner" stays disabled until at least one week and one recipe are chosen.
    document.querySelectorAll('[data-planner-form]').forEach(form => {
        form.addEventListener('change', () => refreshSubmit(form));
        refreshSubmit(form);
    });

    function refreshSubmit(form) {
        if (!form) return;
        const weeks = checkedCount(form, 'weekIds');
        const recipes = checkedCount(form, 'recipeIds');
        form.querySelector('[data-submit-planner]').disabled = weeks === 0 || recipes === 0;
    }

    // Checkboxes count only when ticked. Hidden fields (one week, or the recipe page's recipe) always count.
    function checkedCount(form, name) {
        return [...form.querySelectorAll(`[name="${name}"]`)]
            .filter(input => input.type !== 'checkbox' || input.checked).length;
    }

    // Live filtering of the recipe picker. Only the planner page has one.
    const search = document.getElementById('pick-search');
    if (!search) return;

    const season = document.getElementById('pick-season');
    const category = document.getElementById('pick-category');
    const cards = [...document.querySelectorAll('.pick-card')];
    const emptyNote = document.querySelector('.pick-empty');

    // Enter in the search box would submit the whole form, so it only filters.
    search.addEventListener('keydown', event => {
        if (event.key === 'Enter') event.preventDefault();
    });

    [search, season, category].forEach(control => {
        control.addEventListener('input', applyFilters);
        control.addEventListener('change', applyFilters);
    });

    // Thymeleaf leaves out an attribute whose value is empty, so a missing value counts as "".
    function words(value) {
        return (value || '').split(' ').filter(Boolean);
    }

    function applyFilters() {
        const text = search.value.trim().toLowerCase();
        let shown = 0;

        cards.forEach(card => {
            const matches = (card.dataset.name || '').includes(text)
                && (!season.value || words(card.dataset.season).includes(season.value))
                && (!category.value || words(card.dataset.categories).includes(category.value));

            card.hidden = !matches;
            if (matches) shown++;
        });
        emptyNote.hidden = shown > 0;
    }
})();
