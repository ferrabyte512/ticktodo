# Competitor Analysis: Tick ToDo

Research date: 2026-10-20

## 1. Scope and method

Tick ToDo is a personal to-do app for Android. It is built with Kotlin, Jetpack Compose and Room (offline-first), uses Firebase Auth with Google sign-in, and syncs through Firestore. It will ship reminders and a home-screen widget. This review covers eight named products and three smaller open-source Android projects, so we can decide what to build in v1 and what to leave out.

Method:

- Features, plans and prices come from official pricing pages, help centers and Google Play listings. Each Play listing was fetched on 2026-10-20 to record its rating, install band and "Updated on" date.
- For the open-source projects, release dates, last push, star counts and dependencies come from the GitHub REST API and the raw build files (`libs.versions.toml`, `AndroidManifest.xml`).
- Complaints come from vendor troubleshooting pages, the Microsoft Tech Community forum, GitHub issue trackers and aggregated review sites (Capterra, GetApp). Each one is cited in section 3. Reddit could not be searched directly; one r/tasks thread was found through a mirror.
- Anything we could not confirm from a primary source is marked "not verified". Ratings and install counts change daily, so treat them as snapshots.

Products checked for maintenance:

| Project | Latest release | Last push | Status |
|---|---|---|---|
| Super Productivity | v19.2.0 (2026-10-11) | 2026-10-20 | Active |
| Vikunja (server) | v2.7.0 (2026-10-02) | 2026-10-20 | Active |
| Vikunja mobile app (go-vikunja/app) | tag v0.0.26-alpha | 2026-09-14 | Alpha, not on Play Store (per project download page) |
| Tasks.org | 15.13 (2026-10-14) | 2026-10-20 | Active |
| jtx Board | v2.17.03 (2026-10-05) | 2026-10-18 | Active |
| OpenTasks (dmfs) | 1.4.2 (2021-03-21) | 2022-11-08 | Inactive. Excluded |
| Errands | 46.2.10 (2025-12-22) | 2026-10-17 | Active, but Linux/GNOME only, no Android app. Excluded |

## 2. Competitor matrix

### 2.1 Feature coverage

Legend: Y = yes, N = no, P = paid tier only, NV = not verified.

| App | Android app (Play, 2026-10-20) | Quick capture | Lists / projects | Tags | Priority | Subtasks | Recurrence | Reminders | Widget | Offline | Sync backend |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Todoist | Y, 4.6, 10M+, updated 2026-10-20 | Quick Add with natural-language dates, voice "Ramble", Quick Settings tile | Y (free: 5 projects) | Y (labels) | Y | Y | Y | Y (custom/multiple: P) | Y (home + lock screen) | Y (help article; unsynced edits lost on logout) | Proprietary cloud |
| TickTick | Y, 4.6, 10M+, updated 2026-09-24 | Y (voice and quick add) | Y (free: 9 lists, 99 tasks each) | Y | Y | Y (free: 19 checklist items) | Y | Y (free: 2 per task, Premium: 5; figures read from a garbled table) | Y | NV | Proprietary cloud |
| Google Tasks | Y, 4.6, 10M+, updated 2026-10-15 | Basic add sheet; create from Gmail | Y | N | Star only | Y | Y (a task with subtasks cannot repeat) | Y (via date/time notification) | Y (home screen help article) | NV | Google account |
| Microsoft To Do | Y, 4.6, 10M+, updated 2026-07-14 | Widget add; Outlook flagged email | Y | Hashtags in task text (third-party guides; NV on official docs) | Importance flag (NV) | Y ("steps") | Y | Y | Y | NV | Microsoft 365 / Exchange |
| Apple Reminders | N (no Android app) | Control Center / Lock Screen add (iOS 26, per 9to5Mac) | Y, plus sections | Y (#tags, iCloud only) | NV | Y | NV | Y | iOS only | NV | iCloud |
| Any.do | Y, 4.4, 10M+, updated 2026-09-10 | Y, voice, WhatsApp | Y | Color tags: P | NV | Y | P (per pricing page) | Y; location: P; WhatsApp: P | Y (calendar widget) | NV | Proprietary cloud |
| Super Productivity | Y, 4.8, 100K+, updated 2026-10-11; F-Droid | Y | Y (projects) | Y | NV | Y | Y | Y (Android issues open, see section 3) | NV | Y (local-first, no account) | Optional: SuperSync, WebDAV, Dropbox, local file |
| Vikunja | No store app; alpha APK and PWA | Web quick-add | Y (list, kanban, Gantt, table views) | Y (labels) | Y | Y | Y (weekly or monthly intervals) | Y | N (no stable app) | N (server-centric) | Self-hosted Go server or Vikunja Cloud |
| Tasks.org | Y, 4.6, 500K+, updated 2026-10-14; F-Droid | Y | Y | Y | Y | Y (unlimited nesting) | Y | Y, including location | Y (customizable) | Y (no account needed) | Local only, or Tasks.org Cloud, Google Tasks, Microsoft To Do, CalDAV, DAVx5, EteSync, DecSync CC |
| jtx Board | Y, 4.4, 50K+, updated 2026-07-23 | Y | Y (collections) | Y (categories) | Y | Y | Y | Y (alarms, full-screen alarm activity) | Y (Glance) | Y | CalDAV via the separate DAVx5 app |

### 2.2 Assessment

"Cost to match" is our estimate of the effort for Tick ToDo to reach parity on the parts relevant to a personal to-do app. It does not cover team or AI features.

| App | Free vs paid (as published 2026-10-20) | Strengths | Drawbacks | Cost to match |
|---|---|---|---|---|
| Todoist | Beginner free (5 projects, 3 filters, basic reminders). Pro adds 300 projects, 150 filters, custom reminders, calendar layout and AI. Price did not render on the official page. Third-party sources (June 2026) give about US$5/month billed yearly and US$7 billed monthly: not verified | Fastest text capture; consistent list, board and calendar views; good offline handling | Low free project cap; multiple reminders are Pro-only; its own help asks users to keep the app open and turn off battery saver for reminders | High |
| TickTick | Free with caps. Premium US$49.99/year | Widest single-app feature set: calendar, habits, Pomodoro, widgets, Wear OS | Free caps (lists, tasks per list, reminders per task) push users to upgrade; dense UI | High |
| Google Tasks | Free | Very simple; built into Gmail and Calendar | No tags, no priority beyond a star; repeat is blocked when a task has subtasks; notifications depend on Calendar settings (vendor blog) | Low |
| Microsoft To Do | Free | My Day daily planning; Outlook integration; free shared lists | Long-running reports of late or missing reminders; Microsoft's own Android guide sends users into OEM battery settings | Med |
| Apple Reminders | Free with Apple devices | Sections, tags, smart lists, auto-categorize (iOS 26) | No Android or web app for Android users | Med (reference only) |
| Any.do | Personal free. Premium US$7.99/month or US$4.99/month billed yearly | Calendar plus tasks in one; WhatsApp reminders | Recurring tasks and location reminders are paid; reviewers report upsell pressure and billing distrust | Med |
| Super Productivity | Free, MIT. Optional SuperSync service | Local-first, no account, end-to-end encrypted sync option, op-log sync design | Angular/Capacitor app, not native Android; open Android reminder bugs | High |
| Vikunja | Free self-host (AGPL-3.0). Cloud from €4/month or €40/year | Clean REST API (OpenAPI v2), CalDAV, rich views | No production Android app; not offline-first; needs a server | High |
| Tasks.org | Free core (unlimited tasks, reminders, recurrence, location, Google Tasks and Microsoft To Do sync). Name-your-price from US$0.99/year for CalDAV/EteSync/DAVx5 and themes. Cloud US$2.49/month or US$29.99/year | Native Kotlin/Compose/Room stack close to ours; reminders and recurrence free; works with no account | Utilitarian UI with many settings; self-hosted sync is behind a payment on Play | Med |
| jtx Board | Free plus a Pro flavor on Play (unlocks editing remote entries; price not verified) | iCalendar-native (VTODO/VJOURNAL), journals and tasks linked | Needs DAVx5 for sync; niche audience | Med |

## 3. Recurring complaints

**1. Reminders arrive late or not at all on Android.** This complaint has the most evidence, and it covers paid, free and open-source apps alike.
- Todoist's troubleshooting article tells Android users to turn off battery-saving mode, keep the app "open or minimized", and contact their device manufacturer if settings differ. [S5]
- Microsoft's Android reminder guide says reminder notifications "are dependent on the system settings of the Android device you are using". It then gives per-OEM steps for Samsung, Xiaomi, Huawei, OnePlus, Oppo and others. [S13]
- A Microsoft Tech Community thread, "To Do task reminders not always working", ran from January 2021 to April 2024 with 37 replies. Users report reminders arriving 5 to 10 minutes late or not at all, with no staff answer visible. [S14]
- Tasks.org documents the same issue and points users to dontkillmyapp.com. [S20] An r/tasks thread (read through a mirror) reports alerts up to half an hour late even after following those steps on Samsung. [S21]
- Super Productivity issue #10424, "Reminder/Notifications do not work at all (Android)", opened 2026-10-01 and is still open. [S24] The Vikunja mobile app shipped a release note titled "Notifications for Android 14 now work". [S29]
- Root causes: Android 14 denies `SCHEDULE_EXACT_ALARM` by default for new installs of apps targeting API 33+, and exact-alarm calls throw `SecurityException` without it. [S30] OEM battery managers also kill background apps; dontkillmyapp.com ranks Huawei, Xiaomi, OnePlus and Samsung as the worst offenders. [S31]

**2. Basic features are behind a paywall.**
- Any.do lists recurring tasks, location reminders and color tags as Premium. [S9] Capterra reviewers call out recurring tasks being paid and frequent upgrade prompts. [S10]
- TickTick's free plan caps lists at 9, tasks per list at 99 and reminders per task at 2. [S6]
- Todoist Beginner caps personal projects at 5 and limits multiple custom reminders to Pro. [S1][S3]

**3. Sync conflicts and data loss at the edges.**
- Todoist warns that unsynced offline changes are deleted if the user logs out or clears data. [S4]
- Super Productivity issue #10861 describes a delete that conflicts with edits on another device: the task stays on the deleting device only until restart. [S25]
- Microsoft says recurring reminders depend on an internet connection. [S13]

**4. Recurrence edge cases.**
- Vikunja issues #4103 and #4104 (October 2026): a task repeating monthly on the 31st skipped February and moved to March 3. [S28]
- Google Tasks blocks repeat on any task that has subtasks. [S12]

**5. Missing organization tools in the simplest apps.** Google Tasks has no tags or priority levels, only a star and manual ordering. [S11]

**6. No usable Android client.** Apple Reminders has no Android app. Vikunja's own download page called its app "an alpha state at best" and recommended the PWA. [S29]

## 4. Selections

### Best UI/UX benchmark: Todoist
- Task capture is the strongest in the group. One Quick Add field takes a date, list, label and reminder in a single line of text, and the same capture is available from a Quick Settings tile and from home and lock-screen widgets. [S2]
- Navigation is consistent: any project can switch between list, board and calendar layout without changing how a task looks or behaves. [S2]
- The offline behavior is spelled out to users, including an on-screen offline indicator. [S4]
- Accessibility was not audited in this review (not verified). We should benchmark capture speed and consistency against Todoist, and benchmark accessibility against Material 3 guidance instead.

### Best functionality benchmark: TickTick
- One app covers lists, tags, priorities, subtasks/checklists, recurrence, multiple reminders per task, calendar views, habits, Pomodoro and widgets, and syncs across Android, Wear OS, iOS, web and desktop. [S7]
- Premium is US$49.99/year, below Todoist and Any.do on an annual basis. [S6][S9]
- For v1 it shows the full feature set a user may expect eventually. We should match the core (lists, tags, priority, subtasks, recurrence, reminders, widget) and skip habits, Pomodoro and calendar views for now.

### Best architecture/performance benchmark: Tasks.org
- It uses the same stack we plan to use: Kotlin, Compose (Multiplatform), Room 3.0.3, WorkManager 2.12 and Hilt. [S19]
- It is local-first. Without an account it is fully functional, and sync is a pluggable layer over the local database (Google Tasks, Microsoft To Do, CalDAV, EteSync, DecSync CC, Tasks.org Cloud). [S16][S17]
- Its manifest declares `SCHEDULE_EXACT_ALARM`, a receiver for `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`, and `BOOT_COMPLETED` rescheduling. This is the pattern Android 14 requires. [S19][S30]
- It has been maintained continuously since the Astrid fork, with a release on 2026-10-14 and 15,600+ commits. [S18]
- For conflict handling we will also take ideas from Super Productivity's design notes: one user intent becomes one operation, conflicts are detected with vector clocks, last-writer-wins is the default, and deletes are explicitly marked. [S23]

### Most actionable weakness to solve in v1: reminders that do not fire reliably on Android
- The evidence spans the market leaders (Todoist, Microsoft To Do) and open-source apps (Tasks.org users, Super Productivity #10424, the Vikunja app). In every case the vendor's answer is to send the user into OEM battery settings. [S5][S13][S14][S20][S24]
- The cause is known and documented: exact-alarm permission is denied by default on Android 14 [S30], and OEM background killing is widespread [S31]. A careful implementation can fix most of it without a server.
- It fits our architecture. Reminders can be scheduled locally from Room with `AlarmManager`, they do not depend on Firestore being reachable, and they are rescheduled on boot, time change, app update and permission grant.
- It is cheap to build compared with the alternatives: an NLP quick-add or a calendar view costs more and is less directly linked to user trust.
- We will also keep all reminders and recurrence free. That answers complaint 2 at no engineering cost.

## 5. Design decisions for Tick ToDo

All decisions describe general usability patterns. We will not reuse any competitor's name, logo, icons, colors, layouts, copy, onboarding text or screenshots. All visual design and wording will be our own.

| # | Lesson from research | Tick ToDo decision (original) |
|---|---|---|
| 1 | Reminders fail when exact alarms are denied or the app is killed | Schedule each reminder with `AlarmManager.setExactAndAllowWhileIdle()` when `canScheduleExactAlarms()` is true. Otherwise use `setWindow()` and show a visible "approximate timing" label on the task. Reschedule everything on `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED`, `MY_PACKAGE_REPLACED` and the exact-alarm permission-change broadcast. Do not depend on `USE_EXACT_ALARM`, because Play restricts it to alarm-clock and calendar apps. |
| 2 | Vendors put the burden of OEM battery settings on the user, in a help article | Build a "Reminder health" screen in Settings. It checks notification permission, exact-alarm permission, the battery-optimization exemption and the notification channel state, and links to the right system screen for each failing check. It runs once after the first reminder is created and again if a reminder fires more than 2 minutes late. |
| 3 | Users cannot tell whether a reminder was missed | Record scheduled and fired times locally. If a reminder is detected as missed at app start, show it in an "Overdue reminders" banner instead of dropping it silently. |
| 4 | Reminders and recurrence are paywalled elsewhere | v1 has no paid tier. Unlimited lists, tasks and reminders, and recurrence are free. |
| 5 | Fast capture matters most (Todoist benchmark) | Keep an always-visible bottom capture field on the main screen. Recognized date and time phrases in English (for example "tomorrow 9am", "every Monday") become removable chips beneath the field, so the user sees what was parsed before saving. Choose the list and priority with chips, not typed symbols. |
| 6 | Capture must work outside the app | Ship a Glance widget with a one-tap add action and today's tasks, plus a Quick Settings tile that opens the capture sheet directly. |
| 7 | Offline edits can be lost on logout or conflict | Room is the single source of truth. Firestore sync runs from an outbox table through WorkManager. Logout is blocked while the outbox has pending changes, and we tell the user why. |
| 8 | Delete/edit conflicts leave devices out of step | Each task stores `updatedAt` (server timestamp), a per-device `clientId` and a `deleted` tombstone. Last-writer-wins applies per field group. Tombstones are kept for 30 days so every device sees deletions. |
| 9 | Recurrence edge cases (31st of the month, tasks with subtasks) | Store recurrence as a small RRULE subset. Clamp month-end dates to the last valid day. Allow recurrence on tasks with subtasks by resetting the subtasks when the next occurrence is created. Unit-test month-end, leap-year and DST transitions. |
| 10 | The simplest apps lack tags and priority; the richest feel dense | v1 has lists, up to 3 priority levels, tags, one level of subtasks, due date and time, multiple reminders and recurrence. Habits, timers and calendar views are out of scope. Advanced fields stay collapsed in the task editor until used. |
| 11 | A required account is a barrier; local-first apps let users start immediately | The app works fully without signing in. Google sign-in is optional and only turns on sync. Local data is uploaded on first sign-in after a confirmation step. |
| 12 | Accessibility not verified in competitors | Use Material 3 components with content descriptions on every icon button, touch targets of at least 48dp, support for 200% font scale, and TalkBack labels on chips and checkboxes. Run Accessibility Scanner before each release. |

## 6. Sources

All sources accessed 2026-10-20.

| ID | Source | URL |
|---|---|---|
| S1 | Todoist pricing | https://www.todoist.com/pricing |
| S2 | Todoist on Google Play | https://play.google.com/store/apps/details?id=com.todoist |
| S3 | Todoist help: reminders | https://www.todoist.com/help/articles/introduction-to-reminders-9PezfU |
| S4 | Todoist help: use Todoist while offline | https://www.todoist.com/help/articles/use-todoist-while-offline-4rbaZw |
| S5 | Todoist help: troubleshoot notifications | https://www.todoist.com/help/articles/troubleshoot-notifications-not-received-from-todoist-OKO3ezsK |
| S5a | Todoist price, third party (not official) | https://routine.co/blog/posts/todoist-pricing |
| S6 | TickTick Premium page | https://ticktick.com/about/upgrade |
| S7 | TickTick on Google Play | https://play.google.com/store/apps/details?id=com.ticktick.task |
| S8 | Any.do on Google Play | https://play.google.com/store/apps/details?id=com.anydo |
| S9 | Any.do pricing | https://www.any.do/pricing |
| S10 | Any.do reviews, Capterra | https://www.capterra.com/p/173614/Any-do/reviews/ |
| S11 | Google Tasks on Google Play | https://play.google.com/store/apps/details?id=com.google.android.apps.tasks |
| S12 | Google Tasks help: add or edit a task | https://support.google.com/tasks/answer/7675838 |
| S12a | Google Tasks help: overview | https://support.google.com/tasks/answer/7675772 |
| S12b | TasksBoard blog on Google Tasks notifications (vendor source) | https://tasksboard.com/blog/google-tasks-notifications-not-working |
| S13 | Microsoft: troubleshoot reminder issues in To Do for Android | https://support.microsoft.com/en-us/todo/troubleshoot-reminder-issues-in-microsoft-to-do-for-android |
| S14 | Microsoft Tech Community: To Do reminders not always working | https://techcommunity.microsoft.com/discussions/to-do/to-do-task-reminders-not-always-working/2082879 |
| S15 | Microsoft To Do on Google Play | https://play.google.com/store/apps/details?id=com.microsoft.todos |
| S16 | Tasks.org sync docs | https://tasks.org/docs/sync |
| S17 | Tasks.org pricing | https://tasks.org/pricing |
| S18 | Tasks.org GitHub repository | https://github.com/tasks/tasks |
| S19 | Tasks.org build and manifest files | https://github.com/tasks/tasks/blob/main/gradle/libs.versions.toml ; https://github.com/tasks/tasks/blob/main/app/src/main/AndroidManifest.xml |
| S20 | Tasks.org notification troubleshooting | https://tasks.org/docs/troubleshooting_notifications |
| S21 | r/tasks "Task alerts come late" (mirror) | https://redlib.hackliberty.org/r/tasks/comments/1l3icu3/task_alerts_come_late |
| S22 | Super Productivity GitHub repository | https://github.com/super-productivity/super-productivity |
| S23 | Super Productivity architecture decisions | https://github.com/super-productivity/super-productivity/blob/master/ARCHITECTURE-DECISIONS.md |
| S24 | Super Productivity issue #10424 | https://github.com/super-productivity/super-productivity/issues/10424 |
| S25 | Super Productivity issue #10861 | https://github.com/super-productivity/super-productivity/issues/10861 |
| S26 | Vikunja GitHub repository | https://github.com/go-vikunja/vikunja |
| S27 | Vikunja features and pricing | https://vikunja.io/features |
| S28 | Vikunja issues #4103, #4104 | https://github.com/go-vikunja/vikunja/issues/4104 |
| S29 | Vikunja app releases and download page history | https://github.com/go-vikunja/app/releases ; https://kolaente.dev/vikunja/website/commit/b9d109560b55887a36e0df7ddbd89401417e404c |
| S30 | Android 14: exact alarms denied by default | https://developer.android.com/about/versions/14/changes/schedule-exact-alarms |
| S31 | Don't kill my app | https://dontkillmyapp.com/ |
| S32 | jtx Board GitHub repository | https://github.com/TechbeeAT/jtxBoard |
| S33 | jtx Board on Google Play | https://play.google.com/store/apps/details?id=at.techbee.jtx |
| S34 | Super Productivity on Google Play | https://play.google.com/store/apps/details?id=com.superproductivity.superproductivity |
| S35 | Tasks.org on Google Play | https://play.google.com/store/apps/details?id=org.tasks |
| S36 | Apple Support: tag items in Reminders | https://support.apple.com/guide/iphone/tag-items-iph474a6685c/ios |
| S37 | Apple Support: organize reminders (sections, subtasks) | https://support.apple.com/en-us/119953 |
| S38 | 9to5Mac: everything new in Reminders with iOS 26 | https://9to5mac.com/2025/10/13/heres-everything-new-in-reminders-with-ios-26/ |
| S39 | OpenTasks repository (inactive) | https://github.com/dmfs/opentasks |
| S40 | Errands repository (Linux only) | https://github.com/mrvladus/Errands |
