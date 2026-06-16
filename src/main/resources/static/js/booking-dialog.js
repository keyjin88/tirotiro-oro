(function () {
    const dialog = document.getElementById("booking-dialog");
    if (!dialog) {
        return;
    }

    const content = document.getElementById("booking-dialog-content");

    function closeDialog() {
        if (dialog.open) {
            dialog.close();
        }
    }

    function openDialog(trigger) {
        const date = trigger.dataset.date || "";
        const returnUrl = trigger.dataset.returnUrl || window.location.pathname + window.location.search;
        const params = new URLSearchParams();
        if (date) {
            params.set("date", date);
        }
        if (returnUrl) {
            params.set("returnUrl", returnUrl);
        }

        htmx.ajax("GET", `/bookings/modal?${params.toString()}`, {
            target: "#booking-dialog-content",
            swap: "innerHTML"
        }).then(() => {
            dialog.showModal();
            if (window.initBookingDateSync) {
                window.initBookingDateSync(content);
            }
            const startsAt = content.querySelector("[data-booking-starts-at]");
            if (startsAt) {
                startsAt.focus();
            }
        });
    }

    dialog.addEventListener("click", (event) => {
        if (event.target === dialog) {
            closeDialog();
        }
    });

    dialog.addEventListener("close", () => {
        content.innerHTML = "";
    });

    document.addEventListener("click", (event) => {
        const trigger = event.target.closest("[data-booking-dialog]");
        if (trigger) {
            event.preventDefault();
            event.stopPropagation();
            openDialog(trigger);
            return;
        }

        if (event.target.closest("[data-dialog-close]") && dialog.contains(event.target)) {
            event.preventDefault();
            closeDialog();
        }
    });
})();
