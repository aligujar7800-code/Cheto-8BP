import os
import json
import re
import requests

TOKEN = "" # Token will be asked at runtime or read from local_env.json
REPO = "aligujar7800-code/Cheto-8BP"
BUILD_GRADLE_PATH = "app/build.gradle"
UPDATE_JSON_PATH = "update.json"

def update_version():
    new_version_code = input("Enter new versionCode (current is in build.gradle): ")
    new_version_name = input("Enter new versionName (e.g., 1.1): ")
    release_notes = input("Enter release notes: ")

    # Update build.gradle
    with open(BUILD_GRADLE_PATH, 'r') as f:
        content = f.read()
    
    content = re.sub(r'versionCode \d+', f'versionCode {new_version_code}', content)
    content = re.sub(r'versionName "[^"]+"', f'versionName "{new_version_name}"', content)
    
    with open(BUILD_GRADLE_PATH, 'w') as f:
        f.write(content)

    # Update update.json
    update_data = {
        "versionCode": int(new_version_code),
        "versionName": new_version_name,
        "apkUrl": f"https://github.com/{REPO}/releases/latest/download/app-release.apk",
        "releaseNotes": release_notes
    }
    
    with open(UPDATE_JSON_PATH, 'w') as f:
        json.dump(update_data, f, indent=2)

    print(f"Updated to version {new_version_name} ({new_version_code})")
    return new_version_name, release_notes

def git_push(version_name):
    os.system("git add .")
    os.system(f'git commit -m "Release v{version_name}"')
    os.system("git push origin main")
    print("Code pushed to GitHub.")

def create_github_release(version_name, notes):
    url = f"https://api.github.com/repos/{REPO}/releases"
    headers = {
        "Authorization": f"token {TOKEN}",
        "Accept": "application/vnd.github.v3+json"
    }
    data = {
        "tag_name": f"v{version_name}",
        "name": f"Release v{version_name}",
        "body": notes,
        "draft": False,
        "prerelease": False
    }
    
    response = requests.post(url, headers=headers, json=data)
    if response.status_code == 201:
        release_id = response.json()['id']
        print(f"Release created successfully. ID: {release_id}")
        return release_id
    else:
        print(f"Failed to create release: {response.text}")
        return None

def upload_apk(release_id):
    apk_path = "app/build/outputs/apk/release/app-release-unsigned.apk" # Change if needed
    if not os.path.exists(apk_path):
        # Try finding any apk in the build folder
        for root, dirs, files in os.walk("app/build/outputs/apk"):
            for file in files:
                if file.endswith(".apk") and "release" in root:
                    apk_path = os.path.join(root, file)
                    break
    
    if not os.path.exists(apk_path):
        print("APK not found! Please build the APK in Android Studio first.")
        return

    print(f"Uploading {apk_path}...")
    url = f"https://uploads.github.com/repos/{REPO}/releases/{release_id}/assets?name=app-release.apk"
    headers = {
        "Authorization": f"token {TOKEN}",
        "Content-Type": "application/vnd.android.package-archive"
    }
    
    with open(apk_path, 'rb') as f:
        response = requests.post(url, headers=headers, data=f)
    
    if response.status_code == 201:
        print("APK uploaded successfully!")
    else:
        print(f"Failed to upload APK: {response.text}")

if __name__ == "__main__":
    if not TOKEN:
        TOKEN = input("Enter your GitHub Personal Access Token: ")
    
    v_name, notes = update_version()
    git_push(v_name)
    
    print("\nIMPORTANT: Please make sure you have built the Release APK in Android Studio.")
    choice = input("Do you want to create a GitHub Release and upload the APK now? (y/n): ")
    if choice.lower() == 'y':
        r_id = create_github_release(v_name, notes)
        if r_id:
            upload_apk(r_id)
