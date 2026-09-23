(function () {
    const rowContainer = document.getElementById('ingredient-rows');
    const rowTemplate = document.getElementById('ingredient-row-template');
    const addRowButton = document.getElementById('add-ingredient-row');

    if (!rowContainer) return;

    const MIN_LENGTH = 2;
    const DEBOUNCE_MS = 300;

    let nextIndex = rowContainer.querySelectorAll('.ingredient-row').length;
    let debounceTimer;
    let draggedRow = null;

    addRowButton.addEventListener('click', () => {
        const fragment = rowTemplate.content.cloneNode(true);

        fragment.querySelectorAll('[name]').forEach(field => {
            field.name = field.name.replace('__INDEX__', nextIndex);
        });

        // Carry the section down from the row above, so a group is typed once, not per row.
        const rows = rowContainer.querySelectorAll('.ingredient-row');
        const previousSection = rows.length
            ? sectionBoxOf(rows[rows.length - 1]).value
            : '';

        // The template holds two <tr>s: the section row, then the ingredient row.
        rowContainer.appendChild(fragment);
        nextIndex++;

        const newRow = rowContainer.lastElementChild;
        sectionBoxOf(newRow).value = previousSection;
        syncSections();

        const newInput = newRow.querySelector('.ingredient-name');
        newInput.focus();
        syncLanguage(newInput);
    });

    rowContainer.querySelectorAll('.ingredient-name').forEach(syncLanguage);
    syncSections();

    rowContainer.addEventListener('click', event => {
        if (event.target.classList.contains('row-remove')) {
            const row = event.target.closest('.ingredient-row');
            sectionRowOf(row).remove();
            row.remove();
        }

        if (event.target.classList.contains('section-toggle')) {
            const row = event.target.closest('.ingredient-row');
            const sectionRow = sectionRowOf(row);

            if (sectionRow.hidden) {
                // Start a section here. Typing into the box renames the whole group below.
                sectionRow.hidden = false;
                event.target.classList.add('is-open');
                sectionBoxOf(row).focus();
            } else {
                // Remove this heading: the group rejoins whatever section sits above it.
                const rowAbove = sectionRow.previousElementSibling;
                const inherited = rowAbove ? sectionBoxOf(rowAbove).value : '';
                groupOf(row).forEach(member => sectionBoxOf(member).value = inherited);
                sectionRow.hidden = true;
                event.target.classList.remove('is-open');
            }
        }
    });

    // Drag reordering. Only the handle arms the row (draggable stays off otherwise,
    // so text selection inside the name/qty/unit inputs keeps working normally).
    rowContainer.addEventListener('mousedown', event => {
        const handle = event.target.closest('.drag-handle');
        if (!handle) return;
        handle.closest('.ingredient-row').draggable = true;
    });

    document.addEventListener('mouseup', () => {
        rowContainer.querySelectorAll('.ingredient-row').forEach(row => {
            if (!row.classList.contains('dragging')) row.draggable = false;
        });
    });

    rowContainer.addEventListener('dragstart', event => {
        const row = event.target.closest('.ingredient-row');
        if (!row) return;
        draggedRow = row;
        event.dataTransfer.effectAllowed = 'move';
        event.dataTransfer.setData('text/plain', '');
        row.classList.add('dragging');
    });

    rowContainer.addEventListener('dragover', event => {
        if (!draggedRow) return;
        event.preventDefault();

        const targetRow = event.target.closest('.ingredient-row');
        if (!targetRow || targetRow === draggedRow) return;

        const rect = targetRow.getBoundingClientRect();
        const insertAfter = event.clientY - rect.top > rect.height / 2;
        movePair(draggedRow, targetRow, insertAfter);
    });

    rowContainer.addEventListener('drop', event => event.preventDefault());

    rowContainer.addEventListener('dragend', () => {
        if (draggedRow) {
            draggedRow.classList.remove('dragging');
            draggedRow.draggable = false;
        }
        draggedRow = null;
        renumberRows();
        syncSections();
    });

    // Moves a row together with its own section row, so the section name
    // (which belongs to this specific line) travels with it.
    function movePair(row, targetRow, insertAfter) {
        const rowSection = sectionRowOf(row);
        const targetSection = sectionRowOf(targetRow);

        if (insertAfter) {
            targetRow.after(rowSection, row);
        } else {
            targetSection.before(rowSection, row);
        }
    }

    // Field names encode the list index (ingredients[2].name), so after a drag
    // the DOM order and the submitted order have to be renumbered back in sync.
    function renumberRows() {
        const rows = rowContainer.querySelectorAll('.ingredient-row');
        rows.forEach((row, index) => {
            renumberFields(sectionRowOf(row), index);
            renumberFields(row, index);
        });
        nextIndex = rows.length;
    }

    function renumberFields(tr, index) {
        tr.querySelectorAll('[name]').forEach(field => {
            field.name = field.name.replace(/\[\d+\]/, '[' + index + ']');
        });
    }

    // Every ingredient row is preceded by its own section row, so the pair is
    // always (row.previousElementSibling, row).
    function sectionRowOf(row) {
        return row.previousElementSibling;
    }

    function sectionBoxOf(row) {
        return sectionRowOf(row).querySelector('.ingredient-section');
    }

    // The rows a heading governs: this one, then every row below until the next
    // row that has its own heading open.
    function groupOf(row) {
        const members = [row];
        let nextSectionRow = row.nextElementSibling;

        while (nextSectionRow && nextSectionRow.hidden) {
            const nextRow = nextSectionRow.nextElementSibling;
            if (!nextRow) break;
            members.push(nextRow);
            nextSectionRow = nextRow.nextElementSibling;
        }
        return members;
    }

    // Show the section row only where a new section starts — the same rule the detail
    // page uses to print a heading. Rows that merely continue a section keep the value
    // but stay tidy; the + on any row opens it if the cook wants a new heading there.
    function syncSections() {
        let previous = null;
        rowContainer.querySelectorAll('.ingredient-row').forEach(row => {
            const value = sectionBoxOf(row).value.trim();
            const startsSection = value !== '' && value !== previous;

            sectionRowOf(row).hidden = !startsSection;
            row.querySelector('.section-toggle').classList.toggle('is-open', startsSection);
            previous = value;
        });
    }

    rowContainer.addEventListener('input', event => {
        if (event.target.classList.contains('ingredient-section')) {
            const row = event.target.closest('.section-row').nextElementSibling;
            groupOf(row).forEach(member => sectionBoxOf(member).value = event.target.value);
            return;
        }

        if (!event.target.classList.contains('ingredient-name')) return;

        const input = event.target;
        clearTimeout(debounceTimer);

        const query = input.value.trim();

        if (query.length === 0) {
            const hidden = idField(input);
            if (hidden) hidden.value = '';
        }
        syncLanguage(input);

        if (query.length < MIN_LENGTH) {
            hide(input);
            return;
        }
        debounceTimer = setTimeout(() => fetchSuggestions(input, query), DEBOUNCE_MS);
    });

    document.addEventListener('click', event => {
        if (!event.target.closest('.name-cell')) {
            rowContainer.querySelectorAll('.suggestions').forEach(list => {
                list.hidden = true;
            });
        }
    });

    async function fetchSuggestions(input, query) {
        const response = await fetch(
            '/api/ingredients/autocomplete?q=' + encodeURIComponent(query));

        if (!response.ok) {
            hide(input);
            return;
        }
        render(input, await response.json());
    }

    function render(input, suggestions) {
        const list = input.parentElement.querySelector('.suggestions');
        list.innerHTML = '';

        suggestions.forEach(suggestion => {
            const item = document.createElement('li');
            item.textContent = suggestion.canonicalName;

            if (suggestion.matchedName !== suggestion.canonicalName) {
                const matched = document.createElement('em');
                matched.textContent = ' — ' + suggestion.matchedName
                    + (suggestion.matchedLanguage ? ' (' + suggestion.matchedLanguage + ')' : '');
                item.appendChild(matched);
            }

            item.addEventListener('click', () => {
                input.value = suggestion.matchedName;

                const hidden = idField(input);
                if (hidden) hidden.value = suggestion.id;

                syncLanguage(input);
                list.hidden = true;
            });
            list.appendChild(item);
        });

        list.hidden = suggestions.length === 0;
    }

    function idField(input) {
        return input.parentElement.querySelector('input[type="hidden"]');
    }

    function langField(input) {
        return input.parentElement.querySelector('.ingredient-language');
    }

    // The language only matters when findOrCreate is about to run — i.e. the cook typed
    // something but never picked a suggestion, so there is no id to identify the ingredient.
    function syncLanguage(input) {
        const lang = langField(input);
        if (!lang) return;

        const hidden = idField(input);
        const isLinked = hidden && hidden.value !== '';

        lang.hidden = isLinked || input.value.trim() === '';
    }

    function hide(input) {
        const list = input.parentElement.querySelector('.suggestions');
        list.innerHTML = '';
        list.hidden = true;
    }
})();