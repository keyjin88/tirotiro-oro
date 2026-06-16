(function () {
    const dialog = document.getElementById("reason-dialog");
    if (!dialog) {
        return;
    }

    const form = dialog.querySelector("#reason-dialog-form");
    const titleEl = dialog.querySelector("#reason-dialog-title");
    const messageEl = dialog.querySelector("#reason-dialog-message");
    const reasonEl = dialog.querySelector("#reason-dialog-reason");
    const optionalHint = dialog.querySelector("#reason-dialog-optional");

    function closeDialog() {
        if (dialog.open) {
            dialog.close();
        }
    }

    dialog.addEventListener("click", (event) => {
        if (event.target === dialog) {
            closeDialog();
        }
    });

    dialog.addEventListener("close", () => {
        form.reset();
        reasonEl.required = false;
        messageEl.hidden = true;
        optionalHint.hidden = true;
    });

    document.addEventListener("click", (event) => {
        const trigger = event.target.closest("[data-reason-dialog]");
        if (trigger) {
            event.preventDefault();
            form.action = trigger.dataset.action || "";
            titleEl.textContent = trigger.dataset.title || "";

            const message = trigger.dataset.message || "";
            if (message) {
                messageEl.textContent = message;
                messageEl.hidden = false;
            } else {
                messageEl.hidden = true;
            }

            const required = trigger.dataset.reasonRequired === "true";
            reasonEl.required = required;
            optionalHint.hidden = required;

            dialog.showModal();
            reasonEl.focus();
            return;
        }

        if (event.target.closest("[data-dialog-close]")) {
            event.preventDefault();
            closeDialog();
        }
    });

    form.addEventListener("submit", (event) => {
        if (reasonEl.required && !reasonEl.value.trim()) {
            event.preventDefault();
            reasonEl.focus();
        }
    });
})();
