# Tick ToDo: Policy and Legal Research Note

**Note:** This is factual research prepared for review by a qualified lawyer. It is not legal advice and should not be relied on as such.

- Prepared: 20 October 2026. Every source linked below was accessed on **2026-10-20** unless another date is given.
- Scope: Android app "Tick ToDo" (Kotlin/Compose). It uses Firebase Authentication (Google sign-in via Credential Manager), Cloud Firestore for per-user task sync, Room, local notifications and exact alarms, WorkManager and a home-screen widget. v1 has no ads and no analytics SDK, with an AdMob hook planned for later. The developer is based in India and will publish worldwide on Google Play.
- "Not verified" means I could not confirm the point against a primary or official source during this research.

---

## 1. Google Play

### 1.1 Target API level
- New apps and app updates must target **Android 16 (API level 36)** or higher. The deadline was **31 August 2026**. [Play Console Help: Target API level requirements](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en)
- Existing apps must target **API 35 or higher** to stay available to new users on newer Android versions. [same source]
- Play Console offered an extension to **1 November 2026**. That extension does not help a brand-new app submitted now. [same source]
- Android's own requirements page: [developer.android.com: Meet Google Play's target API level requirement](https://developer.android.com/google/play/requirements/target-sdk).
- Next step: secondary reports expect the API 37 requirement in 2027. Not verified.

### 1.2 Account deletion (in-app plus web link)
- Any app that lets users create an account must provide **an in-app path** to delete the account and its associated data. [Play Console Help: Understanding Google Play's app account deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en)
- The developer must also provide a **web link** where users can request deletion without reinstalling the app. The page must reference the app or developer name exactly as it appears on the store listing. The link goes in the Data safety form. [same source]
- Data may be kept for legitimate reasons (security, fraud prevention, legal compliance), but the retention must be clearly disclosed, for example in the privacy policy. [same source]
- If you use service providers (here, Firebase/Google Cloud), you must delete your own copy and ask the providers to delete theirs. [same source]
- Signing in with a Google account still counts as account creation for this policy. The policy covers "accounts created within the app" and outside flows that lead into it. [same source]

### 1.3 Data safety form
- The form is mandatory for every app on a closed, open or production track, including apps that collect no data. A privacy policy link is required. [Play Console Help: Data safety section](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en)
- "Collection" means transmitting data off the device. "Sharing" means transferring it to a third party. Transfers to a **service provider** that processes data on the developer's behalf are generally not declared as sharing. Data that is only processed on the device does not need to be declared. [same source]
- The developer is responsible for data that third-party SDKs collect. [same source]
- **Firebase Authentication** collects some data automatically: the Firebase user agent, **IP address** (for security and abuse prevention), SDK user-agent strings and the Firebase App ID. Depending on usage it also collects display name, **email address**, contact info from third-party providers and a generated **User ID**. [Firebase: Data disclosure for Google Play](https://firebase.google.com/docs/android/play-data-disclosure)
- **Cloud Firestore** automatically collects only the Firebase user agent. It also stores developer-defined data (here, task content) and attaches the Auth User ID to requests. [same source]
- Firebase encrypts data in transit using HTTPS. [same source]
- **AdMob (future):** the Google Mobile Ads SDK automatically collects and shares IP address (used to estimate approximate location), user product interactions, diagnostic information, and device identifiers (advertising ID, app set ID) for advertising, analytics and fraud prevention. It uses TLS in transit. [AdMob: Data disclosure](https://developers.google.com/admob/android/privacy/play-data-disclosure)
- Likely v1 declaration, for counsel and the developer to confirm:
  - Personal info: Name, Email address, User IDs. Collected, not shared. Purposes: App functionality, Account management.
  - App activity: "Other user-generated content" (task text). Collected, not shared. Purpose: App functionality.
  - Encrypted in transit: yes. Deletion: yes.
  - Whether to declare the IP address Firebase Auth logs (and under which category) is a judgement call. Not verified.

### 1.4 Exact alarms: SCHEDULE_EXACT_ALARM vs USE_EXACT_ALARM
- **USE_EXACT_ALARM** is granted at install and the user cannot revoke it. Play limits it to apps whose **core, user-facing functionality** needs precisely timed actions. The examples given are **alarm or timer apps** and **calendar apps that show event notifications**. Using it for non-core features is not allowed. [Play Console Help: Permissions and APIs that Access Sensitive Information](https://support.google.com/googleplay/android-developer/answer/9888170?hl=en)
- Apps "will not be able to publish a version of their app with this permission in the manifest unless they qualify". [Android 14 behavior change: Schedule exact alarms are denied by default](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms)
- **SCHEDULE_EXACT_ALARM** is granted by the user and can be revoked by the user or the system. It is **not pre-granted** to new installs of apps targeting Android 13+, and it is denied on Android 14 after backup and restore. [Android: Schedule alarms](https://developer.android.com/develop/background-work/services/alarms/schedule)
- The app must call `canScheduleExactAlarms()` before calling `setExact*()` or `setAlarmClock()`, or it gets a `SecurityException`. It can send the user to `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` and should listen for `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` to reschedule. [same source; Android 14 page above]
- **To-do and reminder apps are not named** in the Play policy or the Android docs as qualifying for USE_EXACT_ALARM. Declaring USE_EXACT_ALARM for a to-do app therefore carries a real rejection risk. The conservative reading is: **use SCHEDULE_EXACT_ALARM, ask for it in context, and fall back to inexact alarms (`setAndAllowWhileIdle`/`setWindow`) or WorkManager when it is denied.** This is an interpretation, not stated policy text.
- USE_FULL_SCREEN_INTENT is auto-granted only to alarm and calling apps (for apps targeting Android 14+), and requires a Play declaration. Tick ToDo should not need it. [Play Console Help: Foreground service and full-screen intent requirements](https://support.google.com/googleplay/android-developer/answer/13392821?hl=en)

### 1.5 POST_NOTIFICATIONS
- On Android 13+ the app must declare and request the runtime permission `POST_NOTIFICATIONS`. Notifications are off by default for new installs. [Android: Notification runtime permission](https://developer.android.com/develop/ui/views/notifications/notification-permission)
- Best practice: request it in context, for example when the user first sets a reminder, rather than at launch. Check `areNotificationsEnabled()` before posting. [same source]

### 1.6 Foreground services
- Apps targeting Android 14+ that use foreground services must declare each FGS type in Play Console. Each declaration needs a description, the user impact if the work is deferred, and a video. [Play Console Help: Foreground service and full-screen intent requirements](https://support.google.com/googleplay/android-developer/answer/13392821?hl=en)
- Reminders built on AlarmManager plus WorkManager do not need a foreground service. If none is used, no declaration is needed.
- Play lists further FGS/FSI policy changes with a deadline of **27 January 2027**. I have not reviewed their content. [Play Console Help: policy deadlines list](https://support.google.com/googleplay/android-developer/answer/12253906?hl=en)

### 1.7 New personal developer accounts: testing requirement
- Personal accounts **created after 13 November 2023** must run a **closed test with at least 12 testers who have been opted in for at least 14 consecutive days** before applying for production access. A tester who opts out and back in restarts their 14-day count. [Play Console Help: App testing requirements for new personal developer accounts](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en)
- Production and pre-registration stay disabled until the requirement is met. The production-access application asks about the closed test, the app and production readiness. Review usually takes 7 days or less. [same source]
- Organization accounts are exempt, according to secondary sources. Not verified on the Google page above.

### 1.8 Developer verification (Android-wide, announced for 2026)
- From **30 September 2026**, certified Android devices in **Brazil, Indonesia, Singapore and Thailand** require apps installed from participating stores to come from verified developers. Google plans to expand verification **globally in 2027**. [Android Developers: Developer verification](https://developer.android.com/developer-verification)
- Google says Play "automatically registers 99% of apps". Any remaining apps can be registered manually in Play Console. Limited-distribution accounts (up to 20 devices) exist for hobbyists. [same source]
- India is not in the 2026 first wave. Whether Play Console identity verification fully satisfies developer verification is not verified. Check the Play Console guide under the same site.

### 1.9 Target audience and content
- Every app must declare its target age groups. If any group includes children, the **Families Policy** applies, and only Families Self-Certified Ads SDKs may serve ads to children or users of unknown age. [Play Console Help: Target audience and app content](https://support.google.com/googleplay/android-developer/answer/9867159?hl=en)
- Listing graphics that look aimed at children can lead to rejection unless the app follows the Families Policy. [same source]

### 1.10 App access instructions for reviewers
- If parts of the app sit behind a login, provide reusable credentials that work from any location, plus step-by-step instructions. For third-party sign-in such as Google, give full account details and steps. [Play Console Help: Requirements for providing sign-in details](https://support.google.com/googleplay/android-developer/answer/15748846?hl=en)
- Tick ToDo should provide a dedicated test Google account with 2-step verification arranged so a reviewer can use it. If the app works without signing in, say so.

---

## 2. Firebase

### 2.1 Roles, terms and data collected
- For Firebase services, Google is generally the **data processor** (GDPR) or **service provider** (CCPA), and the customer is the controller or business. The governing terms are the [Firebase Data Processing and Security Terms](https://firebase.google.com/terms/data-processing-terms). [Firebase: Privacy and Security in Firebase](https://firebase.google.com/support/privacy)
- Per the privacy page, Firebase Authentication processes email addresses, phone numbers, user agents and IP addresses (and passwords, for password auth). [same source]
- **Retention:** Firebase Auth keeps logged IP addresses for a few weeks. After a user is deleted, their data is removed from live and backup systems **within 180 days**. [same source]
- **Location:** Firebase Authentication processes data **only in US data centres**. Most other services can run in any Google Cloud location. [same source]
- **Transfers:** Google LLC is certified under the EU-US, UK Extension and Swiss-US Data Privacy Frameworks. Firebase also publishes [Standard Contractual Clauses](https://firebase.google.com/terms/firebase-sccs). [same source]

### 2.2 Firestore data location
- The location is chosen when the database is created and **cannot be changed later**. Moving means creating a new database. [Firebase: Cloud Firestore locations](https://firebase.google.com/docs/firestore/locations)
- Indian regions are available: **asia-south1 (Mumbai)** and **asia-south2 (Delhi)**. Multi-regions include nam5 (US) and eur3 (Europe). [same source]
- Even with Firestore in India, Firebase Auth data is held in the US (see 2.1). The privacy policy should say this.

### 2.3 Deleting a user and their data
- Client-side: call `FirebaseUser.delete()`. **"To delete a user, the user must have signed in recently"**. Otherwise the call fails (`FirebaseAuthRecentLoginRequiredException`) and the user must reauthenticate, for example with a fresh Google credential. [Firebase: Manage users on Android](https://firebase.google.com/docs/auth/android/manage-users)
- In Firestore, **deleting a document does not delete its subcollections**. Deleting whole collections from a mobile client is "not recommended" for security and performance reasons. Do it from a trusted server environment. [Firebase: Delete data from Cloud Firestore](https://firebase.google.com/docs/firestore/manage-data/delete-data)
- The **"Delete User Data" extension** deletes Firestore, Realtime Database or Storage data keyed on the UID when an Auth user is deleted. It needs the **Blaze** plan and Cloud Functions. [Firebase Extensions Hub: Delete User Data](https://extensions.dev/extensions/firebase/delete-user-data)
- **Important:** the Firebase Extensions service is **deprecated and shuts down on 31 March 2027**. After that date, installed extensions can no longer be reconfigured or uninstalled. Firebase recommends migrating to "function kits" or forking the code. [Firebase: Extensions FAQ](https://firebase.google.com/docs/extensions/faq-and-troubleshooting?hl=en); [Migrate to function kits](https://firebase.google.com/docs/extensions/users/migrate)
- Practical options: (a) client-side deletion of the user's task documents followed by `user.delete()`, which works on the free Spark plan but is not atomic; or (b) a Cloud Function (Blaze plan) triggered on Auth user deletion that does a recursive delete.

### 2.4 Security rules for per-user data
- The documented owner-only pattern is `match /users/{userId}` with `allow read, update, delete: if request.auth != null && request.auth.uid == userId;`. [Firebase: Security rules conditions](https://firebase.google.com/docs/firestore/security/rules-conditions)
- The documentation's example `create` rule only checks that the user is signed in. Tick ToDo's rules should also check `request.auth.uid == userId` on create and validate fields using `request.resource.data`. Rules are not filters, so queries must be scoped to the user's own path. [same source]
- Recommended structure: `users/{uid}/tasks/{taskId}`, deny by default, no collection-group reads across users.

---

## 3. Sign in with Google via Credential Manager

- Legacy Google Sign-In for Android (`GoogleSignInClient` in `play-services-auth`) is **deprecated** and "will be removed from the Google Play services Auth SDK in a future release". Google recommends migrating to **Credential Manager**. Authorization for Google APIs (scopes) is handled separately by **AuthorizationClient**. [Android: Migrate from legacy Google Sign-In](https://developer.android.com/identity/sign-in/legacy-gsi-migration)
- One Tap for Android is listed under "Legacy APIs" in Android's identity documentation. Credential Manager replaces its bottom-sheet behaviour. I found no specific removal date for either legacy API. Not verified.
- Recommended UX: show the Credential Manager bottom sheet (`GetGoogleIdOption`, with `setFilterByAuthorizedAccounts`/`setAutoSelectEnabled` for returning users) **and** a separate "Sign in with Google" button (`GetSignInWithGoogleOption`). [Android: Sign in with Google via Credential Manager](https://developer.android.com/identity/sign-in/credential-manager-siwg)
- Firebase's Android guide uses `androidx.credentials:credentials`, `androidx.credentials:credentials-play-services-auth` and `com.google.android.libraries.identity.googleid:googleid`. It passes the ID token to `GoogleAuthProvider.getCredential(idToken, null)` and must use the **Web** OAuth client ID as the server client ID. [Firebase: Authenticate with Google on Android](https://firebase.google.com/docs/auth/android/google-signin)

---

## 4. AdMob (future)

- **EEA, UK and Switzerland:** publishers using AdMob must use a **Google-certified CMP integrated with the IAB TCF** when serving ads there. This applies from 16 January 2024 (EEA/UK) and 31 July 2024 (Switzerland). Google's own **User Messaging Platform (UMP)** is one such option. [AdMob Help: Consent management requirements for EEA, UK and Switzerland](https://support.google.com/admob/answer/13554020?hl=en)
- UMP flow: call `requestConsentInfoUpdate()` on **every launch**, show the form with `loadAndShowConsentFormIfRequired()`, check `canRequestAds()` before requesting ads, and provide a **privacy options entry point** (`showPrivacyOptionsForm()`) when required. Debug geography works only on test devices. [Google Mobile Ads SDK: Get started with UMP](https://developers.google.com/admob/android/privacy)
- **US state laws:** AdMob offers a "US state regulations" message under Privacy & messaging, plus restricted data processing (RDP) or IAB GPP/US Privacy signals. [AdMob Help: US state regulations](https://support.google.com/admob/answer/10860309); [Mobile Ads SDK: US states privacy laws](https://developers.google.com/admob/android/privacy/us-states)
- Reports say AdMob removed the account-level RDP setting in June 2025. Not verified.
- **Test ads:** during development, use Google's demo ad units (for example, banner `ca-app-pub-3940256099942544/9214589741`, interstitial `.../1033173712`, rewarded `.../5224354917`, native `.../2247696110`, app open `.../9257395921`) or registered test devices. Clicking live ads can get the account flagged for invalid traffic. Remove the test-device code before release. [AdMob: Enable test ads](https://developers.google.com/admob/android/test-ads)
- **AD_ID:** apps targeting Android 13+ must declare `com.google.android.gms.permission.AD_ID` to read the advertising ID. The Ads SDK merges this permission automatically. Apps must use the ad ID instead of other device identifiers for advertising. A deleted ID returns zeros. [Play Console Help: Advertising ID](https://support.google.com/googleplay/android-developer/answer/6048248?hl=en)
- Adding AdMob means updating the Data safety form (device IDs, approximate location, app interactions and diagnostics, collected and shared for ads). Also answer "Contains ads: Yes" in App content.
- In v1, **do not ship** the Ads SDK or the AD_ID permission. If a dependency merges AD_ID, remove it with `tools:node="remove"`, which the advertising ID page describes.
- **Placement:** AdMob guidance warns that placing banners near navigation buttons, menus or interactive elements, floating or moving banners, and close buttons next to interstitials cause accidental clicks and put the app and account at risk. [AdMob Help: Implementation guidance](https://support.google.com/admob/answer/6275345?hl=en); [Banner implementation guidance](https://support.google.com/admob/answer/6293636?hl=en)
- For a list app, keep banners clear of checkboxes, FABs, swipe targets and the widget.
- **AdSense is not for native apps.** AdSense program policies forbid Google ads "integrated into a software application (does not apply to AdMob) of any kind". Native in-app ads must use AdMob or Ad Manager through the Google Mobile Ads SDK. [AdSense Program policies](https://support.google.com/adsense/answer/48182?hl=en)
- Children: if the target audience ever includes children, only Families Self-Certified Ads SDKs may be used (see 1.9). India's DPDP Act also bars targeted advertising directed at children (see 5.1).

---

## 5. India

### 5.1 Digital Personal Data Protection Act, 2023 (DPDP Act)
- Official text: [DPDP Act 2023 (MeitY PDF)](https://www.meity.gov.in/static/uploads/2024/06/2bf1f0e9f04e6fb4f8fef35e82c42aa5.pdf). Tick ToDo's developer would be a **Data Fiduciary**. Firebase/Google would be a **Data Processor**.
- A "child" is anyone **under 18** (s.2(f)). A Data Fiduciary must obtain **verifiable parental consent** before processing a child's data (s.9(1)). It must **not** undertake "tracking or behavioural monitoring of children or targeted advertising directed at children" (s.9(3)).
- Core duties take effect with the Rules (see 5.2):
  - notice (s.5) and consent, with withdrawal as easy as giving consent (s.6)
  - "legitimate uses" (s.7)
  - reasonable security safeguards and breach intimation to the Board and affected users (s.8(5)-(6))
  - erasure once the purpose is served or consent is withdrawn (s.8(7))
  - publishing contact details (s.8(9)) and a grievance mechanism (s.8(10))
  - user rights to access, correction and erasure, grievance redressal and nomination (ss.11-14)
- Cross-border transfers are allowed **except** to countries the government restricts by notification (s.16, a negative list). No such list has been notified that I could find. Not verified.
- Penalties (Schedule, as summarised by the PIB explainer): up to ₹250 crore for failing to keep reasonable security safeguards, up to ₹200 crore each for breach-notification failures and children's-data failures, and up to ₹50 crore for other breaches. [PIB explainer: DPDP Rules, 2025 Notified (PDF)](https://static.pib.gov.in/WriteReadData/specificdocs/documents/2025/nov/doc20251117695301.pdf)

### 5.2 DPDP Rules, 2025: notification and phased commencement
- The Rules are **G.S.R. 846(E), dated 13 November 2025**. [Gazette text, MeitY PDF](https://www.meity.gov.in/static/uploads/2025/11/53450e6e5dc0bfa85ebd78686cadad39.pdf). The PIB explainer gives the notification date as 14 November 2025.
- Commencement under Rule 1:
  - **Rules 1, 2 and 17-21** (definitions; the Data Protection Board, its procedure and appeals): in force on publication, **November 2025**.
  - **Rule 4** (registration and obligations of **Consent Managers**): in force **one year after publication, about 13-14 November 2026**.
  - **Rules 3, 5-16, 22 and 23** (notice, safeguards, breach, retention and erasure, contact person, children, rights, transfers): in force **eighteen months after publication, about 13-14 May 2027**.
- Act sections were brought into force on the same phasing by a companion notification, reported as G.S.R. 843(E) of 13 November 2025. Most operative sections (ss.3-17) and s.44(2) start at 18 months, and ss.6(9) and 27(1)(d) (consent managers) at one year. [Taxmann summary](https://www.taxmann.com/post/blog/govt-notifies-commencement-dates-for-dpdp-act/). Gazette text not retrieved, so this is not verified against the primary source.
- A corrigendum (G.S.R. 892(E), 10 December 2025) to the commencement wording is reported. Not verified.
- MeitY reportedly proposed in January 2026 to compress the 18-month window to 12 months, possibly only for Significant Data Fiduciaries. No amending notification had been found as of September-October 2026 commentary. [MirvoLegal timeline, Sept 2026](https://www.mirvolegal.com/insights/dpdp-rules-2025-compliance-timeline/). Not verified: monitor the Gazette.
- What applies when, from the Gazette text:
  - **Notice (Rule 3):** must stand alone and be in clear, plain language. It must include an itemised description of the personal data, the specific purposes and uses, and a link or means to withdraw consent (as easily as it was given), exercise rights and complain to the Board. Effective **May 2027**.
  - **Consent Managers (Rule 4):** India-incorporated entities registered with the Board, from **November 2026**. Tick ToDo is **not required** to use one.
  - **Security (Rule 6):** encryption/masking, access control, logging and monitoring, backups, processor contracts, and **retaining logs and personal data for one year** for investigation. Effective **May 2027**.
  - **Breach (Rule 7):** tell each affected user **without delay** what happened, its impact, the mitigation and a contact. Tell the Board **without delay**, followed by a detailed report **within 72 hours** (extendable by the Board). Effective **May 2027**.
  - **Retention and erasure (Rule 8):** the Third Schedule's three-year inactivity erasure and 48-hour warning apply only to large e-commerce platforms (≥2 crore users), online gaming intermediaries (≥50 lakh users) and social media intermediaries (≥2 crore users), so not to Tick ToDo. **But Rule 8(3) requires every Data Fiduciary to retain the personal data, traffic data and logs of processing for at least one year from the date of processing** for Seventh Schedule purposes, then erase them. Counsel should reconcile this with account-deletion promises and Play's deletion policy. Effective **May 2027**.
  - **Contact person (Rule 9):** publish prominently in the app or on the website the business contact of a person who can answer data-processing questions. Effective **May 2027**.
  - **Children (Rule 10):** verifiable consent of a parent, who must be an identifiable adult, using reliable identity and age details already held or provided voluntarily, or via a virtual token from an authorised entity such as DigiLocker. Effective **May 2027**.
  - **Rights and grievances (Rule 14):** publish how to exercise rights. Respond to grievances within a **period not exceeding 90 days**. Effective **May 2027**.

### 5.3 IT Act 2000 s.43A and SPDI Rules 2011
- Section 44(2)(a) of the DPDP Act provides that **"section 43A shall be omitted"** from the IT Act. [DPDP Act PDF](https://www.meity.gov.in/static/uploads/2024/06/2bf1f0e9f04e6fb4f8fef35e82c42aa5.pdf)
- Per the commencement reports above, s.44(2) takes effect at the 18-month mark, about May 2027. Until then, s.43A and the **IT (Reasonable Security Practices and Procedures and Sensitive Personal Data or Information) Rules, 2011** continue to apply. [Hogan Lovells (HLC) client note](https://www.hlc.com/en/publications/indias-digital-personal-data-protection-act-2023-brought-into-force-)
- SPDI Rules requirements: a published privacy policy, consent for sensitive personal data (which includes passwords), and a grievance officer. I have not verified these against the official text in this round. Tick ToDo does not handle passwords, because sign-in goes through Google.

### 5.4 IT (Intermediary Guidelines and Digital Media Ethics Code) Rules, 2021
- Intermediaries must publish a **Grievance Officer** and their contact details. Complaints must be acknowledged within **24 hours** and resolved within **15 days**. [IT Rules 2021, as amended 6 April 2023 (MeitY PDF)](https://www.meity.gov.in/static/uploads/2024/02/Information-Technology-Intermediary-Guidelines-and-Digital-Media-Ethics-Code-Rules-2021-updated-06.04.2023-.pdf)
- Whether a private, single-user to-do app that stores a user's own records counts as an "intermediary" under the IT Act's broad definition is a legal question for counsel. Publishing a grievance contact costs little and also covers DPDP Rule 9 and 14 duties.

### 5.5 Consumer Protection Act 2019 and E-Commerce Rules 2020
- The E-Commerce Rules apply to "e-commerce entities" that own or operate a platform for buying or selling goods or services. They require a grievance officer (acknowledge in 48 hours, resolve within one month) and other disclosures. [HSA Advocates summary (PDF)](https://hsalegal.com/wp-content/uploads/2020/09/HSA-Corp-Comm-Law-Policy-The-Consumer-Protection-E-commerce-Rules-2020.pdf). The official notification was not retrieved, so this is not verified.
- A free app with no in-app sales probably falls outside the E-Commerce Rules. That would change with paid features or subscriptions. This is an interpretation for counsel.
- Whether a free-app user is a "consumer" under the Consumer Protection Act depends on consideration. Not verified.

### 5.6 ASCI (if ads are added)
- The Advertising Standards Council of India Code is a self-regulatory code for advertising content. It applies mainly to advertisers and their claims, so for third-party AdMob ads it is of limited direct relevance to the app publisher. If Tick ToDo itself is advertised (store listing, influencer posts), ASCI rules on honesty and disclosure apply. [ASCI Code](https://ascionline.in/the-asci-code/). Detailed applicability not verified.

---

## 6. Other markets (brief)

- **GDPR (EU) and UK GDPR:** they apply to a non-EU/UK developer offering services to people in the EU or UK (Art. 3(2)). [GDPR on EUR-Lex](https://eur-lex.europa.eu/eli/reg/2016/679/oj)
  - Likely lawful basis: **contract** (Art. 6(1)(b)) for account and sync, and **consent** for any future personalised ads.
  - Rights to provide: access, rectification, erasure, portability, objection and complaint to a supervisory authority.
  - Transfers to Google are covered by the Firebase SCCs and Google's DPF certification (see 2.1).
  - Art. 27 requires a non-EU controller to appoint an **EU representative** (and the UK has its own equivalent), unless processing is occasional and low-risk. Whether that exemption covers a continuous sync service is doubtful. Counsel to decide. The exemption scope is not verified.
- **CCPA/CPRA (California):** applies to for-profit businesses with revenue over **$26,625,000** (CPI-adjusted from 1 January 2025), or that buy, sell or share data of 100,000+ California residents or households, or that earn 50%+ of revenue from selling data. [California AG: CCPA](https://oag.ca.gov/privacy/ccpa); [CPPA: Updated monetary thresholds](https://www.cppa.ca.gov/regulations/cpi_adjustment.html)
  - A solo developer with a free, ad-free app is **very unlikely** to meet any threshold. Other US state laws have similar or higher thresholds. Not reviewed individually.
- **COPPA (US):** applies to services directed at children under 13, or with actual knowledge of collecting their data. The amended COPPA Rule took effect **23 June 2025**, with general compliance required by **22 April 2026**. It requires separate opt-in consent for targeted advertising. [Federal Register: COPPA Rule (2025)](https://www.federalregister.gov/documents/2025/04/22/2025-05904/childrens-online-privacy-protection-rule)
  - If Tick ToDo is declared "not for children" and is not child-directed, COPPA should not apply. Avoid child-oriented marketing.

---

## 7. Open-source licences

- AndroidX, Firebase Android SDKs, Kotlin and most Google libraries are under the **Apache License 2.0**. Redistribution requires: giving recipients **a copy of the licence**, marking modified files, **keeping copyright and attribution notices**, and including a readable copy of any **NOTICE** file's attributions (for example on a "third-party notices" screen). [Apache License 2.0, s.4](https://www.apache.org/licenses/LICENSE-2.0)
- Google's guidance: app developers are responsible for showing open-source notices. Google provides the `com.google.android.gms.oss-licenses-plugin` Gradle plugin and `play-services-oss-licenses`, whose `OssLicensesMenuActivity` lists them. [Google Play services: Include open source notices](https://developers.google.com/android/guides/opensource)
- **Material Symbols / Material Icons** are released under **Apache License 2.0**. Attribution is appreciated but not required. [google/material-design-icons on GitHub](https://github.com/google/material-design-icons)
- Check each dependency's licence before release. Anything not under Apache, MIT or BSD needs separate review.

---

## Implications for Tick ToDo: checklist

**Build and manifest**
- [ ] Set `targetSdk = 36` (API 36 is required for new apps). Plan for API 37 in 2027.
- [ ] Declare `SCHEDULE_EXACT_ALARM`. **Do not** declare `USE_EXACT_ALARM`. Gate every exact alarm on `canScheduleExactAlarms()`. Fall back to inexact alarms or WorkManager if denied. Reschedule on boot, on time or timezone change, and on `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`.
- [ ] Request `POST_NOTIFICATIONS` in context, when the first reminder is set, and handle denial gracefully.
- [ ] Avoid foreground services and `USE_FULL_SCREEN_INTENT`. If either becomes necessary, prepare the Play declarations.
- [ ] Do not ship the Ads SDK or `AD_ID` in v1. Inspect the merged manifest and remove any stray `AD_ID`.
- [ ] Add an open-source notices screen (oss-licenses-plugin) reachable from Settings/About.

**Sign-in and backend**
- [ ] Use Credential Manager plus `googleid` (bottom sheet and "Sign in with Google" button) with the Web client ID. No `GoogleSignInClient`.
- [ ] Create the Firestore database in **asia-south1 (Mumbai)**, or deliberately choose another region. This is irreversible. Disclose that Firebase Auth data is processed in the US.
- [ ] Write owner-only rules for `users/{uid}/tasks/{taskId}` (uid check on read/create/update/delete, field validation, default deny). Test them in the emulator.
- [ ] Accept the Firebase Data Processing and Security Terms in the console. Keep a copy, together with the SCCs, for the file.

**Account deletion**
- [ ] In-app: Settings > Delete account. Reauthenticate first, then delete Firestore data (including subcollections), then `user.delete()`, then clear the Room DB, scheduled alarms, WorkManager jobs and widget state.
- [ ] Do not rely on the "Delete User Data" extension (shuts down 31 March 2027). If server-side cleanup is wanted, write a Cloud Function on the Blaze plan.
- [ ] Publish a web deletion-request page naming "Tick ToDo" and the developer name as on the listing. Add it to the Data safety form.
- [ ] State retention clearly in the privacy policy: Firebase Auth's up-to-180-day backup purge, and any DPDP Rule 8(3) one-year log retention counsel decides applies.

**Play Console**
- [ ] Recruit **12+ testers** and keep them opted in for **14 consecutive days** on a closed track, then apply for production. Allow about a week for review.
- [ ] Complete Data safety (Name, Email, User IDs, user-generated content; collected, not shared; encrypted in transit; deletion available), the privacy policy URL, Ads = No, Target audience = 18+ or 13+ (not children), content rating and App access (a working test Google account with steps).
- [ ] Check developer verification status in Play Console ahead of the 2027 global rollout.

**India (DPDP and IT law)**
- [ ] Draft a stand-alone notice with an itemised data list, purposes, a withdraw-consent link, rights and a complaint-to-Board route. Have it ready well before **May 2027**.
- [ ] Publish a contact person or grievance contact in the app and on the website. Set up a process to answer requests within 90 days (DPDP) and, if counsel treats the app as an intermediary, acknowledge within 24 hours and resolve within 15 days (IT Rules).
- [ ] Write a breach response plan: notify users without delay, and the Board without delay plus a detailed report within 72 hours.
- [ ] Decide the age policy. Simplest is an 18+ declaration in-app and on Play, because under-18s need verifiable parental consent under DPDP. Never use behavioural or targeted ads for minors.
- [ ] Keep a privacy policy that also meets the SPDI Rules until s.43A is repealed (about May 2027).
- [ ] Monitor the Gazette for any amendment shortening the DPDP timeline.

**Future AdMob**
- [ ] Integrate UMP (EEA/UK/CH consent, US state message, privacy-options entry point). Gate ads on `canRequestAds()`.
- [ ] Use only demo ad unit IDs and test devices during development.
- [ ] Update Data safety (ad ID, approximate location, interactions, diagnostics; shared for advertising), set Ads = Yes, and update the privacy policy.
- [ ] Place banners away from checkboxes, the FAB, swipe areas and navigation. No ads in the widget or in notifications.

**Other markets**
- [ ] Counsel to decide on an EU/UK Art. 27 representative and confirm the lawful bases. CCPA is unlikely to apply. COPPA is avoided by a non-child audience.
