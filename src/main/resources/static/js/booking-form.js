(function () {
    function initBookingDateSync(root) {
        const startsAt = root.querySelector("[data-booking-starts-at]");
        const endsAt = root.querySelector("[data-booking-ends-at]");
        if (!startsAt || !endsAt || startsAt.dataset.dateSyncBound === "true") {
            return;
        }

        startsAt.dataset.dateSyncBound = "true";

        const datePart = (value) => value ? value.slice(0, 10) : "";
        const endOfDayFor = (value) => {
            const date = datePart(value);
            return date ? `${date}T23:59` : "";
        };
        let previousStartDate = datePart(startsAt.value);

        startsAt.addEventListener("change", () => {
            const nextEnd = endOfDayFor(startsAt.value);
            if (!nextEnd) {
                previousStartDate = datePart(startsAt.value);
                return;
            }

            const currentEndDate = datePart(endsAt.value);
            if (!endsAt.value || currentEndDate === previousStartDate) {
                endsAt.value = nextEnd;
                endsAt.dispatchEvent(new Event("change", { bubbles: true }));
            }
            previousStartDate = datePart(startsAt.value);
        });
    }

    window.initBookingDateSync = initBookingDateSync;

    document.addEventListener("DOMContentLoaded", () => {
        initBookingDateSync(document);
    });

    document.body.addEventListener("htmx:afterSwap", (event) => {
        initBookingDateSync(event.target);
    });
})();
