(function () {
    const dialog = document.getElementById('confirm-dialog');
    const message = document.getElementById('confirm-message');
    if (!dialog) return;

    let pendingForm = null;

    // Any form carrying data-confirm asks first.
    document.addEventListener('submit', event => {
        const form = event.target;
        if (!form.dataset.confirm) return;

        event.preventDefault();
        pendingForm = form;
        message.textContent = form.dataset.confirm;
        dialog.showModal();
    });

    dialog.addEventListener('click', event => {
        if (event.target.hasAttribute('data-confirm-cancel')) {
            dialog.close();
            pendingForm = null;
        }
        if (event.target.hasAttribute('data-confirm-ok')) {
            dialog.close();
            pendingForm.submit();
        }
    });
})();