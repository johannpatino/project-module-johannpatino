// "Copy link" on the invites page. The link box is also selected when clicked, in case copying is blocked.
(function () {
    const button = document.getElementById('copy-invite');
    const box = document.getElementById('invite-link');
    if (!button || !box) return;

    box.addEventListener('focus', () => box.select());

    button.addEventListener('click', async () => {
        box.select();
        try {
            await navigator.clipboard.writeText(box.value);
            button.textContent = 'Copied';
        } catch (e) {
            button.textContent = 'Press Ctrl+C to copy';
        }
    });
})();
