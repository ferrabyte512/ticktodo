# Play Console — exact answers for v1.0.0

Everything here reflects the current build. Re-check when an SDK, permission
or feature changes.

## App details

| Field | Value |
|---|---|
| App name | Tick ToDo |
| Default language | English (United Kingdom) — or en-US; the strings are British-spelled ("colour", "organise") |
| App or game | App |
| Free or paid | Free |
| Category | Productivity |
| Contact e-mail | ferrabyte512@gmail.com |
| Website | https://ferrabyte512.github.io/ticktodo/ |
| Privacy policy | https://ferrabyte512.github.io/ticktodo/legal/privacy-policy.html |

## Store listing assets (`docs/release/assets/`)

| Asset | File |
|---|---|
| App icon 512×512 | `ic_launcher_512.png` (rendered from the adaptive icon by `LauncherIconExportTest`) |
| Feature graphic 1024×500 | `feature_graphic_1024x500.png` |
| Phone screenshots (1080×1920) | `01_today_dark.png` … `10_add_task_light.png` (use 6–8; put `01`, `07`, `03`, `04`, `05`, `02` first) |
| Listing text | `store-listing.md` |

## App content → declarations

| Form | Answer |
|---|---|
| **Privacy policy** | URL above |
| **Ads** | No, my app does not contain ads |
| **App access** | All functionality is available without special access — *but* the reviewer needs a Google account to sign in; choose "All or some functionality is restricted" and add: "Sign in with any Google account; no test credentials are needed." |
| **Content rating** (IARC questionnaire) | Category: Utility, Productivity, Communication or Other. Answer **No** to every content question (violence, sexuality, language, controlled substances, gambling, user-generated content shared with others, location sharing, purchases). Result: Everyone / PEGI 3. |
| **Target audience and content** | Age groups: 13–15, 16–17, 18+ (not designed for children). "Could your app unintentionally appeal to children?" → No. |
| **News app** | No |
| **COVID-19 contact tracing / status** | No |
| **Data safety** | Copy from `data-safety.md` (collects: name, e-mail, user IDs, photos (URL), app interactions, other user-generated content, device/other IDs; all encrypted in transit; deletion available; none shared) |
| **Government app** | No |
| **Financial features** | None |
| **Health** | No health features |
| **Account deletion** | "Yes, users can request deletion" → in-app path: Settings → Delete account; URL: https://ferrabyte512.github.io/ticktodo/legal/delete-account.html; data deleted: all account data; retention: none |

## Policy → Sensitive permissions (shown only if the console asks)

| Permission | Declaration text |
|---|---|
| `SCHEDULE_EXACT_ALARM` | "Core functionality: the app is a to-do list whose reminders are user-scheduled alarms that must fire at the exact minute the user chose." |
| `USE_FULL_SCREEN_INTENT` | "Alarm / reminder: a reminder the user set rings and may wake the screen like an alarm clock; the user can turn this off in system settings (the app checks `canUseFullScreenIntent`)." |
| `POST_NOTIFICATIONS` | Requested at runtime when the first reminder is set. |

Foreground services: none. Background location: none. Accessibility: none.
All-files access: none. Package visibility (`QUERY_ALL_PACKAGES`): none.

## Release notes (en-GB)

```
First release of Tick ToDo.
• Today, Upcoming and a priority matrix for your tasks
• Reminders that ring like an alarm, repeating tasks, subtasks, lists and tags
• Focus timer with breaks, per task
• Works offline and syncs with your Google account across devices
• Home-screen widgets for Today, Upcoming, any list, quick add and the focus timer
```

## Countries

All countries (free). No pricing, no in-app products.

## Testing track

Internal testing first. Tester list: ferrabyte512@gmail.com plus any second
device's account. Install from the Play link, then run
`release-checklist.md` §5 and `two-device-test.md`.
