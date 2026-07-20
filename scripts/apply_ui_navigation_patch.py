from pathlib import Path

path = Path("app/src/main/java/com/opencode/android/ui/OpenCodeApp.kt")
text = path.read_text(encoding="utf-8")

old = '''                    onOpenHistory = {
                        navController.navigate(ROUTE_ACTIVITY) { launchSingleTop = true }
                    },
                    onOpenDrawer = { drawerScope.launch { drawerState.open() } }
'''
new = '''                    onOpenHistory = {
                        navController.navigate(ROUTE_ACTIVITY) { launchSingleTop = true }
                    },
                    onOpenLocalSetup = {
                        navController.navigate(ROUTE_ANDROID_SETUP) { launchSingleTop = true }
                    },
                    onOpenRemoteSetup = {
                        navController.navigate(ROUTE_REMOTE_CONNECTION) { launchSingleTop = true }
                    },
                    onOpenDrawer = { drawerScope.launch { drawerState.open() } }
'''

if new in text:
    print("OpenCodeApp navigation callbacks are already applied.")
elif old in text:
    path.write_text(text.replace(old, new, 1), encoding="utf-8")
    print("Applied direct local/remote setup navigation callbacks.")
else:
    raise SystemExit("Expected ChatHomeScreen call block was not found; refusing to patch.")
