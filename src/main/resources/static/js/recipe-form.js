(function () {
    const rowContainer = document.getElementById('ingredient-rows');
    const rowTemplate = document.getElementById('ingredient-row-template');
    const addRowButton = document.getElementById('add-ingredient-row');

    if (!rowContainer) return;

    const MIN_LENGTH = 2;
    const DEBOUNCE_MS = 300;

    let nextIndex = rowContainer.querySelectorAll('.ingredient-row').length;
    let debounceTimer;

    addRowButton.addEventListener('click', () => {
        const fragment = rowTemplate.content.cloneNode(true);

        fragment.querySelectorAll('[name]').forEach(field => {
            field.name = field.name.replace('__INDEX__', nextIndex);
        });

        // Carry the section down from the row above, so a group is typed once, not per row.
        const rows = rowContainer.querySelectorAll('.ingredient-row');
        const previousSection = rows.length
            ? rows[rows.length - 1].querySelector('.ingredient-section').value
            : '';

        rowContainer.appendChild(fragment);
        nextIndex++;

        rowContainer.lastElementChild.querySelector('.ingredient-section').value = previousSection;

        const newInput = rowContainer.lastElementChild.querySelector('.ingredient-name');
        newInput.focus();
        syncLanguage(newInput);
    });

    rowContainer.querySelectorAll('.ingredient-name').forEach(syncLanguage);

    rowContainer.addEventListener('click', event => {
        if (event.target.classList.contains('row-remove')) {
            event.target.closest('.ingredient-row').remove();
        }
    });

    rowContainer.addEventListener('input', event => {
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