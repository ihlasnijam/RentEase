# RentEase — Smart Rental & Maintenance Management System

Android app (Kotlin + XML) with a Firebase backend, built as a final-year BSc Computer Science project.

## Features
- Landlord and tenant roles with email OTP signup
- Invite-code tenant onboarding (24-hour expiry)
- Landlord verification (Emirates ID and title deed upload) with admin approval
- Maintenance requests with multi-category selection, photo upload, and vendor assignment
- Calendar-aware rent payment module with receipts
- Real-time messaging between landlord and tenant
- Rent splitting with roommates and utility bill tracking
- Document storage and admin web panel (`web/admin.html`)

## Tech stack
Kotlin, XML layouts, Firebase (Auth, Firestore, Storage, Cloud Messaging), Android Studio.

## Setup
1. Create a Firebase project and enable Email/Password sign-in, Firestore, and Storage.
2. Download `google-services.json` from the Firebase console and place it in `app/`.
   (It is git-ignored on purpose; never commit it.)
3. Open the folder in Android Studio and sync Gradle.
4. Run on an emulator or device (API 24+).

## Repository layout
- `app/src/main/java/com/example/rentease/` — all Activities and helper classes
- `app/src/main/res/` — layouts, drawables, values, menu
- `web/` — admin web panel (open in a browser, sign in with the admin account)
- `variants/` — alternative layouts kept for reference, not used by the app
- `docs/` — setup guides, Firestore rules, and reference notes

## Status
Core features are implemented. Known open items: utility bill and roommate add flow under testing,
push notifications need Cloud Functions (Blaze plan), and real payments are simulated.
