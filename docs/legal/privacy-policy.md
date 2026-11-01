# Tick ToDo privacy policy (draft)

**Effective date:** _set on publication_
**Controller:** Ferrabytes (_add legal entity name, address and contact e-mail_)

This draft describes what the current version of the app actually does. It
must be reviewed by a qualified person before publication and the placeholders
must be filled in.

## What the app stores

Tick ToDo is a to-do list. The information you enter — task titles, notes,
tags, lists, due dates, reminders, repeat settings and focus-timer plans — is
stored on your device. When you are signed in, the same information is also
stored in your account in Google Cloud Firestore so that it is available on
your other devices and is not lost if you change phones.

Focus-timer statistics (how long you focused on a task) and your appearance
settings stay on your device only.

## Account

Signing in uses your Google account through Firebase Authentication. We
receive your Google account's unique identifier, e-mail address, display name
and profile picture URL. The e-mail address and name are shown to you in
Settings and are not used for marketing.

You can delete your account from Settings → Delete account. This removes your
tasks and lists from our servers and deletes the authentication record. Data
on your device is removed at the same time.

## Analytics

The app includes Firebase Analytics, which records automatic events such as
app open, screen views and crashes-free sessions, together with coarse device
information (model, OS version, country derived from IP, app version). The app
does not log task content or any custom event containing personal data, and
the Android Advertising ID is not collected (the permission is removed from
the app).

## App integrity

To protect the service from abuse, the app uses Firebase App Check with Google
Play Integrity. Play Integrity sends Google a signal about whether the app and
device are genuine; it does not include your tasks.

## Notifications

Reminders are shown as notifications on your device. On a locked device the
title of the task is hidden until you unlock it.

## What we do not do

* We do not sell or share your data with third parties for their own purposes.
* We do not show advertising in this version.
* We do not read your contacts, location, photos (beyond a wallpaper you
  choose yourself through the system picker), camera or microphone.

## Where data is processed

Firebase services are provided by Google LLC. Data may be processed in Google
data centres outside your country. _State the Firestore region configured for
the project and the applicable transfer mechanism._

## Retention

Your data is kept while your account exists. Deleted tasks are kept as
deletion markers for up to 90 days so that your other devices learn about the
deletion, then removed. Deleting the account removes everything immediately.

## Children

The app is not directed at children under 13 (or the applicable age in your
country) and does not knowingly collect their data.

## Your rights

Depending on where you live you may have the right to access, correct, export
or erase your data. Export: _describe the export feature or how to request
one_. Erasure: Settings → Delete account, or contact us at _e-mail_.

## Changes

We will update this policy when the app's behaviour changes and show the new
effective date here.
