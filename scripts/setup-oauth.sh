#!/bin/bash
set -e
# DraftLock OAuth setup — one command after `gcloud auth login`
# Usage: ./scripts/setup-oauth.sh [PROJECT_ID] [SUPPORT_EMAIL]
# Creates Web OAuth client with correct redirect URI for DraftLock's AppAuth flow.

PROJECT_ID=${1:-draftlock-vault-$(date +%Y%m%d)}
SUPPORT_EMAIL=${2:-$(gcloud config get-value account 2>/dev/null)}
if ! gcloud auth list --filter=status:ACTIVE --format="value(account)" | grep -q .; then
  echo "No active gcloud account. Run: gcloud auth login"
  exit 1
fi
if [ -z "$SUPPORT_EMAIL" ]; then SUPPORT_EMAIL=$(gcloud auth list --filter=status:ACTIVE --format="value(account)" | head -n1); fi

echo "→ Creating project $PROJECT_ID (support: $SUPPORT_EMAIL)..."
gcloud projects create $PROJECT_ID --name="DraftLock" --set-as-default 2>&1 | head -n 20
gcloud config set project $PROJECT_ID

echo "→ Enabling Drive + Docs APIs..."
gcloud services enable drive.googleapis.com docs.googleapis.com --project=$PROJECT_ID

echo "→ Creating OAuth consent screen (External, Testing)..."
# Note: gcloud has no direct consent-screen create; do via Console or API. We create brand via API.
# Fallback: instruct manual step if API fails.
curl -s -X POST -H "Authorization: Bearer $(gcloud auth print-access-token)" \
  -H "Content-Type: application/json" \
  "https://clientauthconfig.googleapis.com/v1/brands" \
  -d "{\"supportEmail\":\"$SUPPORT_EMAIL\",\"applicationTitle\":\"DraftLock\"}" 2>&1 | head -n 20 || echo "Brand may already exist or needs Console UI."

echo "→ Creating Android OAuth client (fixes WEB custom-scheme error)..."
# Android client uses package + SHA-1, no redirect URI to set — custom scheme auto-allowed
SHA1=$(keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android 2>/dev/null | grep SHA1 | awk '{print $2}' | head -n1)
if [ -z "$SHA1" ]; then SHA1=$(./gradlew signingReport 2>/dev/null | grep SHA1 | head -n1 | awk '{print $2}'); fi
echo "   Detected SHA-1: ${SHA1:-(not found — will prompt manual)}"
CLIENT_JSON=$(curl -s -X POST -H "Authorization: Bearer $(gcloud auth print-access-token)" \
  -H "Content-Type: application/json" \
  "https://clientauthconfig.googleapis.com/v1/brands/*/clients" \
  -d "{\"displayName\":\"DraftLock Android\",\"clientType\":\"ANDROID\",\"android\":{\"packageName\":\"com.draftlock.app\",\"sha1Fingerprint\":\"$SHA1\"}}" 2>&1 || true)
echo "$CLIENT_JSON" | head -n 40

if echo "$CLIENT_JSON" | grep -q "clientId"; then
  CLIENT_ID=$(echo "$CLIENT_JSON" | grep -o '"clientId"[[:space:]]*:[[:space:]]*"[^"]*"' | head -n1 | cut -d'"' -f4)
  echo "→ Android Client ID: $CLIENT_ID"
else
  echo "→ Auto-creation failed (likely needs Console UI for Android client)."
  echo "   Manual: console.cloud.google.com → Credentials → Create OAuth client → Android"
  echo "   Package: com.draftlock.app → SHA-1: $SHA1 (from keytool/gradlew signingReport)"
  echo "   Then: echo \"GOOGLE_CLIENT_ID=xxx.apps.googleusercontent.com\" > local.properties"
  exit 0
fi

PREFIX=$(echo "$CLIENT_ID" | cut -d'.' -f1)
REDIRECT="com.googleusercontent.apps.${PREFIX}:/oauth2redirect"
echo "→ Android redirect (auto-allowed, no Console field): $REDIRECT"

echo "GOOGLE_CLIENT_ID=$CLIENT_ID" > local.properties
echo "GOOGLE_CLIENT_ID=$CLIENT_ID" > /tmp/local.properties
echo "✓ local.properties created with $CLIENT_ID"
echo "✓ Also add GitHub secret: gh secret set GOOGLE_CLIENT_ID --body \"$CLIENT_ID\" --repo geojoshuaaquino-source/Draftlock"
echo "→ Redirect URI to add in Console (if not auto): $REDIRECT"
