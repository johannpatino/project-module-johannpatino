// On a recipe page, "Add to planner" sends in the background. The page stays where it is, so the
// browser's Back button goes back to the page the cook came from.
(function () {
    const form = document.querySelector('form[data-add-in-place]');
    if (!form) return;

    const dialog = form.closest('dialog');
    const submitButton = form.querySelector('[data-submit-planner]');
    const error = form.querySelector('[data-add-error]');
    const trigger = document.querySelector('[data-open-dialog="' + dialog.id + '"]');
    const triggerLabel = trigger.textContent;

    form.addEventListener('submit', async event => {
        event.preventDefault();
        submitButton.disabled = true;
        error.hidden = true;
        try {
            // The server answers with a redirect to the planner. It is not followed, so nothing else loads.
            const response = await fetch(form.action, {
                method: 'POST',
                body: new URLSearchParams(new FormData(form)),
                credentials: 'same-origin',
                redirect: 'manual',
            });
            if (response.type !== 'opaqueredirect' && !response.ok) throw new Error('HTTP ' + response.status);

            dialog.close();
            trigger.textContent = 'Added to the planner ✓';
            setTimeout(() => trigger.textContent = triggerLabel, 2500);
        } catch (failure) {
            error.hidden = false;
        } finally {
            submitButton.disabled = false;
        }
    });
})();
