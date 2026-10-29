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
    // While a card is held, the other cards step aside to show where it would land, and the move is saved
    // in the background, so the page does not reload.
    const EDGE_PX = 90;         // within this distance of the top or bottom of the window, the page scrolls
    const SCROLL_STEP_PX = 14;  // how far each step scrolls while a card is held there
    const SHIFT_MS = 180;       // how long the other cards take to step aside
    const moveForm = document.getElementById('move-form'); // only its security token is used
    let drag = null;
    let lastPointer = null;
    let scrollTimer = null;
    let saving = Promise.resolve(); // moves are sent one after another, in the order they were made

    if (document.querySelector('.plan-week')) {
        document.addEventListener('pointerdown', startDrag);

        document.addEventListener('pointermove', event => {
            if (!drag || event.pointerId !== drag.pointerId) return;
            lastPointer = event;
            placeGhost(event);
            updateTarget(event);
        });

        document.addEventListener('pointerup', event => endDrag(event, true));
        document.addEventListener('pointercancel', event => endDrag(event, false));
    }

    function startDrag(event) {
        const grip = event.target.closest('[data-grip]');
        if (!grip || event.button !== 0) return;

        const card = grip.closest('[data-card]');
        const rect = card.getBoundingClientRect();
        const container = containerOf(card.closest('[data-drop]'));

        const ghost = card.cloneNode(true);
        ghost.classList.add('card-ghost');
        ghost.style.width = rect.width + 'px';
        document.body.appendChild(ghost);

        // The card leaves the layout. An empty slot of the same size takes its place until it is dropped.
        const slot = document.createElement('div');
        slot.className = 'card-slot';
        slot.style.height = rect.height + 'px';
        card.before(slot);
        card.style.display = 'none';

        const index = cardsIn(container).indexOf(card);
        drag = {
            card,
            ghost,
            slot,
            pointerId: event.pointerId,
            offsetX: event.clientX - rect.left,
            offsetY: event.clientY - rect.top,
            origin: {
                container,
                index,
                week: card.closest('[data-week-id]').dataset.weekId,
                day: card.dataset.day || '',
                person: card.dataset.person || '',
            },
            target: { container, index },
        };
        lastPointer = event;
        grip.setPointerCapture(event.pointerId);
        placeGhost(event);
        startAutoScroll();
        event.preventDefault();
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
            updateTarget(lastPointer);
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

    // The element that holds a zone's cards: the lane's card row, or the day cell itself.
    function containerOf(zone) {
        return zone.querySelector(':scope > .lane-cards') || zone;
    }

    // The cards directly in a container, top to bottom (the empty slot is not a card).
    function cardsIn(container) {
        return [...container.children].filter(child => child.matches('[data-card]'));
    }

    // A card's middle, without the offset of an animation it may be playing.
    function middleOf(card) {
        const box = card.getBoundingClientRect();
        const shift = new DOMMatrix(getComputedStyle(card).transform).m42;
        return box.top - shift + box.height / 2;
    }

    // Follows the pointer: the slot goes to the place under it, if that is a different place.
    function updateTarget(event) {
        const zone = dropZoneAt(event);
        highlight(zone);
        if (!zone) return;

        const container = containerOf(zone);
        const others = cardsIn(container).filter(card => card !== drag.card);
        let index = others.findIndex(card => event.clientY < middleOf(card));
        if (index === -1) index = others.length;
        if (container === drag.target.container && index === drag.target.index) return;

        drag.target = { container, index };
        moveSlotTo(container, index);
    }

    // Moves the empty slot into a place in a container. The cards that have to move slide to make room.
    function moveSlotTo(container, index) {
        const moved = new Set([...cardsIn(container), ...cardsIn(drag.slot.parentElement)]);
        moved.delete(drag.card);
        const topBefore = new Map([...moved].map(card => [card, card.getBoundingClientRect().top]));

        const others = cardsIn(container).filter(card => card !== drag.card);
        container.insertBefore(drag.slot, others[index] || null);

        for (const [card, top] of topBefore) {
            const shift = new DOMMatrix(getComputedStyle(card).transform).m42;
            const offset = top - (card.getBoundingClientRect().top - shift);
            if (Math.abs(offset) > 0.5) {
                card.animate([{ transform: `translateY(${offset}px)` }, { transform: 'none' }],
                    { duration: SHIFT_MS, easing: 'ease-out' });
            }
        }
    }

    // A lane shows its hint only while it has no cards in it.
    function updateHint(container) {
        const hint = container.querySelector(':scope > .hint');
        if (hint) hint.hidden = cardsIn(container).length > 0;
    }

    function endDrag(event, dropped) {
        if (!drag || event.pointerId !== drag.pointerId) return;

        // Dropped on a column or lane: the slot goes where the pointer is. Anywhere else: back where it came from.
        if (dropped && dropZoneAt(event)) {
            updateTarget(event);
        } else {
            moveSlotTo(drag.origin.container, drag.origin.index);
        }

        const current = drag;
        drag = null;
        lastPointer = null;
        clearInterval(scrollTimer);
        current.ghost.remove();
        highlight(null);

        // The card takes the slot's place, and the place it ends up in is read from the page.
        const container = current.slot.parentElement;
        const children = [...container.children];
        const position = children.slice(0, children.indexOf(current.slot))
            .filter(child => child.matches('[data-card]') && child !== current.card).length;
        current.slot.replaceWith(current.card);
        current.card.style.display = '';

        const zone = container.closest('[data-drop]');
        const to = {
            week: zone.closest('[data-week-id]').dataset.weekId,
            day: zone.dataset.day || '',
            person: zone.dataset.person || '',
            position: zone.dataset.day ? position : null,
        };
        current.card.dataset.day = to.day;
        current.card.dataset.person = to.person;
        updateHint(container);
        updateHint(current.origin.container);

        const { origin } = current;
        const sameSpot = to.week === origin.week && to.day === origin.day && to.person === origin.person
            && position === origin.index;
        if (!sameSpot) saveMove(current.card.dataset.entryId, to);
    }

    // Sends one move to the server. Moves wait for the one before them, so the server gets them in order.
    // The page already shows the new place, so nothing reloads on success.
    function saveMove(entryId, to) {
        const body = new URLSearchParams({
            _csrf: moveForm.querySelector('input[name="_csrf"]').value,
            weekId: to.week,
            day: to.day,
            person: to.person,
            position: to.position === null ? '' : String(to.position),
        });
        saving = saving
            .then(() => fetch('/planner/entries/' + encodeURIComponent(entryId) + '/move', {
                method: 'POST',
                body,
                credentials: 'same-origin',
                redirect: 'manual', // the server answers with a redirect back to the planner, which isn't needed here
            }))
            .then(response => {
                if (response.type !== 'opaqueredirect' && !response.ok) throw new Error('HTTP ' + response.status);
            })
            .catch(() => location.reload()); // the server didn't keep the move: show what it actually has
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
