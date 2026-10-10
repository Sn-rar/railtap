# RailTap

RailTap is an Android app for buying and using digital tickets on Metro Manila's **LRT-1 (Green Line)** and **LRT-2 (Purple Line)**. Users can sign up, log in, pick a line, book a trip, and show a QR pass at the turnstile.

This README explains how the project is organized and what each part does.

---

## How an Android Screen Works

Every screen in the app is made of two files that work together:

| File | Language | What it does |
|---|---|---|
| **`.xml`** (layout) | XML | Controls what the screen **looks like**: buttons, text, images, spacing, and colors. |
| **`.kt`** (activity) | Kotlin | Controls what the screen **does**: button clicks, logging in, moving to another screen, and showing data. |

The Kotlin file connects to its XML file with `setContentView(R.layout.activity_login)`. It finds each element by its ID, for example `findViewById(R.id.signInButton)`.

---

## Project Folders

```
Railtap/
├── app/
│   ├── build.gradle.kts         # App settings and libraries used (Firebase, Material, etc.)
│   ├── google-services.json     # Connects the app to Firebase
│   └── src/main/
│       ├── AndroidManifest.xml  # Lists every screen and which one opens first
│       ├── java/com/example/railtap/   # Kotlin files (the logic)
│       └── res/                        # Resources (layouts, images, colors, fonts)
├── gradle/libs.versions.toml    # Library version numbers
├── build.gradle.kts             # Project-wide build settings
└── settings.gradle.kts          # Project name and modules
```

---

## Kotlin Files (`app/src/main/java/com/example/railtap/`)

| File | What it does |
|---|---|
| `SplashActivity.kt` | Shows the RailTap logo for 3 seconds, then opens the login screen. |
| `LogInActivity.kt` | Signs the user in with their email and password using Firebase. Opens the Dashboard when login succeeds. |
| `SignUpActivity.kt` | Creates a new account with the user's full name, email, and password. Also checks that the passwords match. |
| `DashboardActivity.kt` | The home screen after logging in. |
| `TicketsActivity.kt` | Shows the user's active pass at the top and their other passes in a list. It also shows a countdown until each pass expires. |

---

## Resources (`app/src/main/res/`)

### `layout/`: Screen designs (XML)
| File | Screen |
|---|---|
| `activity_splash.xml` | Splash / loading screen |
| `activity_login.xml` | Login screen |
| `activity_signup.xml` | Sign-up screen |
| `activity_dashboard.xml` | Home: welcome message, trip history, LRT-1 and LRT-2 cards |
| `activity_booking.xml` | Ticket booking: stations, passenger types, payment method, total |
| `activity_tickets.xml` | My Tickets screen |
| `activity_qr.xml` | QR pass for scanning at the turnstile |
| `item_other_pass.xml` | A single ticket card, reused for each pass in the Tickets list |

### `drawable/`: Images, icons, and shapes
- **Images (`.png`):** the app logos (`railtap_logo.png`, `ic_railtap_logo.png`) and train icons (`lrt1_train_icon.png`, `lrt2_train_icon.png`).
- **Icons (`ic_*.xml`):** vector icons such as `ic_home_active`, `ic_tickets_unactive`, `ic_lock`, `ic_email`, `ic_person`, and `ic_arrow_back`.
- **Backgrounds (`bg_*.xml`):** shapes with rounded corners and colors that give cards, pills, and buttons their look, e.g. `bg_ticket_card`, `bg_pill_green`, `bg_purple_pill`, and `bg_warning_banner`.

### `mipmap-*/`: App launcher icon
The icon shown on the phone's home screen, provided in different sizes for different screens.

### `values/`: Shared settings
| File | What it holds |
|---|---|
| `colors.xml` | The app's color palette (e.g. green for LRT-1, purple for LRT-2) |
| `strings.xml` | Text used in the app, such as the app name |
| `themes.xml` | The app's overall style (colors, default look) |
| `ic_launcher_background.xml` | Background color of the launcher icon |

`values-night/themes.xml` holds the theme used when the phone is in dark mode.

### `font/`: Fonts
**Plus Jakarta Sans** in Regular, Bold, ExtraBold, and Italic. `plusjakartasans.xml` groups them into one font family.

### `xml/`: Android settings
`backup_rules.xml` and `data_extraction_rules.xml` control what app data Android is allowed to back up.

---

## Running the App

1. Open the project in Android Studio.
2. Wait for Gradle to finish syncing.
3. Press **Run ▶** on an emulator or phone running Android 7.0 or newer.
