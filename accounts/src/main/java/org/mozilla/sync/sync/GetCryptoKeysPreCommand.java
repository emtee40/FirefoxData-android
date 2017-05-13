/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.sync.sync;

import android.util.Log;
import org.mozilla.sync.impl.FirefoxAccountSyncConfig;
import org.mozilla.gecko.sync.CollectionKeys;
import org.mozilla.gecko.sync.CryptoRecord;
import org.mozilla.gecko.sync.ExtendedJSONObject;
import org.mozilla.gecko.sync.NonObjectJSONException;
import org.mozilla.gecko.sync.crypto.CryptoException;
import org.mozilla.gecko.sync.net.AuthHeaderProvider;
import org.mozilla.gecko.sync.net.SyncStorageRecordRequest;
import org.mozilla.gecko.sync.net.SyncStorageRequestDelegate;
import org.mozilla.gecko.sync.net.SyncStorageResponse;
import org.mozilla.gecko.sync.repositories.domain.RecordParseException;
import org.mozilla.util.IOUtil;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static org.mozilla.sync.impl.FirefoxAccountShared.LOGTAG;

/** A command to get the crypto keys necessary to begin a sync. */
class GetCryptoKeysPreCommand extends SyncClientCommands.SyncClientAsyncPreCommand {
    private static final String CRYPTO_COLLECTION = "crypto";
    private static final String KEYS_ID = "keys";

    @Override
    public void initAsyncCall(final FirefoxAccountSyncConfig syncConfig, final IOUtil.OnAsyncCallComplete<FirefoxAccountSyncConfig> onComplete) {
        if (syncConfig.token == null) {
            onComplete.onError(new IllegalArgumentException("syncConfig.token unexpectedly null."));
            return;
        }

        final SyncStorageRecordRequest request;
        try {
            request = new SyncStorageRecordRequest(
                    FirefoxSyncRequestUtils.getCollectionURI(syncConfig.token, CRYPTO_COLLECTION, KEYS_ID, null));
        } catch (final URISyntaxException e) {
            onComplete.onError(e);
            return;
        }

        request.delegate = new SyncStorageRequestDelegate() {
            @Override
            public void handleRequestSuccess(final SyncStorageResponse response) {
                final CollectionKeys keys = new CollectionKeys();
                final ExtendedJSONObject body;
                try {
                    body = response.jsonObjectBody();
                    keys.setKeyPairsFromWBO(CryptoRecord.fromJSONRecord(body), syncConfig.getSyncKeyBundle());
                } catch (final IOException | NonObjectJSONException | CryptoException | RecordParseException | NoSuchAlgorithmException | InvalidKeyException e) {
                    onComplete.onError(e);
                    return;
                }

                // TODO: persist keys: see EnsureCrypto5KeysStage.
                onComplete.onSuccess(new FirefoxAccountSyncConfig(syncConfig.account, syncConfig.networkExecutor,
                        syncConfig.token, keys));
            }

            @Override
            public void handleRequestFailure(final SyncStorageResponse response) {
                try {
                    onComplete.onError(new Exception("Failed to retrieve crypto keys: " + response.getErrorMessage()));
                } catch (final IOException e) {
                    onComplete.onError(new Exception("Failed to retrieve crypto keys & its error", e));
                }
            }
            @Override public void handleRequestError(final Exception ex) { onComplete.onError(ex); }

            @Override
            public AuthHeaderProvider getAuthHeaderProvider() {
                try {
                    return FirefoxSyncRequestUtils.getAuthHeaderProvider(syncConfig.token);
                } catch (UnsupportedEncodingException | URISyntaxException e) {
                    Log.e(LOGTAG, "getAuthHeaderProvider: unable to get auth header.");
                    return null; // Oh well - we'll make the request we expect to fail and handle the failed request.
                }
            }

            @Override
            public String ifUnmodifiedSince() {
                return null; // This is what EnsureCrypto5KeysStage.ifUnmodifiedSince returns! TODO: do something here?
            }
        };
        request.get();
    }
}