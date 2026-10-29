# RentEase — Android Project File Structure

## Paste each file exactly into these paths in Android Studio:

app/src/main/
├── AndroidManifest.xml
├── java/com/example/rentease/
│   ├── HomeActivity.kt
│   ├── MaintenanceActivity.kt
│   ├── NewRequestActivity.kt
│   ├── PaymentsActivity.kt
│   └── MessagesActivity.kt
└── res/
    ├── layout/
    │   ├── activity_home.xml
    │   ├── activity_maintenance.xml
    │   ├── activity_new_request.xml
    │   ├── activity_payments.xml
    │   ├── activity_messages.xml
    │   └── item_maintenance.xml
    │   └── item_payment.xml
    ├── values/
    │   ├── colors.xml
    │   ├── themes.xml
    │   └── strings.xml
    ├── drawable/
    │   ├── bg_hero_chip.xml
    │   ├── bg_card.xml
    │   ├── bg_badge_amber.xml
    │   ├── bg_badge_blue.xml
    │   ├── bg_badge_green.xml
    │   ├── bg_icon_blue.xml
    │   ├── bg_icon_amber.xml
    │   ├── bg_icon_green.xml
    │   ├── bg_icon_purple.xml
    │   ├── bg_avatar.xml
    │   ├── bg_button_primary.xml
    │   └── bottom_nav_tint.xml
    └── menu/
        └── bottom_nav_menu.xml

## build.gradle dependencies (add to your existing file):
implementation 'androidx.core:core-ktx:1.12.0'
implementation 'androidx.appcompat:appcompat:1.6.1'
implementation 'com.google.android.material:material:1.11.0'
implementation 'androidx.cardview:cardview:1.0.0'
implementation 'androidx.coordinatorlayout:coordinatorlayout:1.2.0'
implementation 'androidx.recyclerview:recyclerview:1.3.2'
