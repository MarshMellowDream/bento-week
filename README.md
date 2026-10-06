# Bento Week

A personal Android app: the Bento Week day plan, with alerts for every step.

- The page in `app/src/main/assets/index.html` is the whole plan. It works out the day, the sleep step, the meal week and the laundry/dishwasher day from the date.
- Each time the app opens, the page hands the next 45 days of steps to the app. The app keeps one system alarm set for the next step, so alerts keep coming with the app closed and after a restart.
- Wake up, leave for work and wind down ring full-screen until dismissed (or for 10 minutes). Everything else is a normal notification.

## Getting the app

Every push to `main` builds the app. Open the newest entry under **Releases** on a phone or tablet and download `BentoWeek.apk`.

Open the app at least once every 6 weeks so it can load the next stretch of alerts.
