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

echo "→ Creating Web OAuth client..."
# Web client with AppAuth custom scheme redirect
# Derive redirect URI will be filled after we get client ID; create without redirect then patch.
CLIENT_JSON=$(curl -s -X POST -H "Authorization: Bearer $(gcloud auth print-access-token)" \
  -H "Content-Type: application/json" \
  "https://clientauthconfig.googleapis.com/v1/brands/*/clients" \
  -d '{"displayName":"DraftLock Web","clientType":"WEB","web":{"redirectUris":[]}}' 2>&1 || true)
echo "$CLIENT_JSON" | head -n 40

# Fallback: use gcloud alpha (if available) or instruct manual creation
if echo "$CLIENT_JSON" | grep -q "clientId"; then
  CLIENT_ID=$(echo "$CLIENT_JSON" | grep -o '"clientId"[[:space:]]*:[[:space:]]*"[^"]*"' | head -n1 | cut -d'"' -f4)
  echo "→ Client ID: $CLIENT_ID"
else
  echo "→ Auto-creation failed (likely needs Console UI for Web client with custom scheme)."
  echo "   Manual: console.cloud.google.com → APIs & Services → Credentials → Create OAuth client → Web application"
  echo "   Name: DraftLock Web → Authorized redirect URIs: com.googleusercontent.apps.<PREFIX>:/oauth2redirect"
  echo "   Then run: echo \"GOOGLE_CLIENT_ID=xxx.apps.googleusercontent.com\" > local.properties"
  exit 0
fi

PREFIX=$(echo "$CLIENT_ID" | cut -d'.' -f1)
REDIRECT="com.googleusercontent.apps.${PREFIX}:/oauth2redirect"
echo "→ Patching redirect URI: $REDIRECT"
curl -s -X PATCH -H "Authorization: Bearer $(gcloud auth print-access-token)" \
  -H "Content-Type: application/json" \
  "https://clientauthconfig.googleapis.com/v1/clients/${CLIENT_ID}" \
  -d "{\"web\":{\"redirectUris\":[\"$REDIRECT\"]}}" 2>&1 | head -n 20

echo "GOOGLE_CLIENT_ID=$CLIENT_ID" > local.properties
echo "GOOGLE_CLIENT_ID=$CLIENT_ID" > /tmp/local.properties
echo "✓ local.properties created with $CLIENT_ID"
echo "✓ Also add GitHub secret: gh secret set GOOGLE_CLIENT_ID --body \"$CLIENT_ID\" --repo geojoshuaaquino-source/Draftlock"
echo "→ Redirect URI to add in Console (if not auto): $REDIRECT"
