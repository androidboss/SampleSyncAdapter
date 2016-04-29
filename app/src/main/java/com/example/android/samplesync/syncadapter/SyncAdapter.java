/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.example.android.samplesync.syncadapter;

import com.example.android.samplesync.Constants;
import com.example.android.samplesync.client.NetworkUtilities;
import com.example.android.samplesync.client.RawContact;
import com.example.android.samplesync.platform.ContactManager;

import org.apache.http.ParseException;
import org.apache.http.auth.AuthenticationException;
import org.json.JSONException;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.SyncResult;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import java.io.IOException;
import java.util.List;

/**
 * SyncAdapter implementation for syncing sample SyncAdapter contacts to the
 * platform ContactOperations provider.  This sample shows a basic 2-way
 * sync between the client and a sample server.  It also contains an
 * example of how to update the contacts' status messages, which
 * would be useful for a messaging or social networking client.
 * SyncAdapter 实现了同步 SyncAdapter 联系人------不好翻译啊。
 * 这个例子展示了2种基本的同步方法，介于客户端和例子服务器之间。
 * 并且包含了一个例子用于更新联系人状态信息,
 * 对于短线和本地网络客户端来说是有用的。
 */
public class SyncAdapter extends AbstractThreadedSyncAdapter {

    private static final String TAG = "SyncAdapter";
    private static final String SYNC_MARKER_KEY = "com.example.android.samplesync.marker";
    private static final boolean NOTIFY_AUTH_FAILURE = true;

    private final AccountManager mAccountManager;

    private final Context mContext;

    public SyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        mContext = context;
        mAccountManager = AccountManager.get(context);
    }

    @Override
    public void onPerformSync(Account account, Bundle extras, String authority,
        ContentProviderClient provider, SyncResult syncResult) {

        try {
            // see if we already have a sync-state attached to this account. By handing
            // This value to the server, we can just get the contacts that have
            // been updated on the server-side since our last sync-up
            // 查看是否之前已经有了跟这个账号对应的同步标记。
            // 通过服务器处理过的这个值，我们就可以获取到在服务器端自从最近的一次同步后的已经被更新的联系人。
            long lastSyncMarker = getServerSyncMarker(account);

            // By default, contacts from a 3rd party provider are hidden in the contacts
            // list. So let's set the flag that causes them to be visible, so that users
            // can actually see these contacts.
            // 默认情况下，来自第三方的联系人provider是隐藏在联系人列表里的。
            // 所以设置一个flag让他们显示出来，
            // 所以用户能真实的看到这些联系人。
            if (lastSyncMarker == 0) {
                ContactManager.setAccountContactsVisibility(getContext(), account, true);
            }

            List<RawContact> dirtyContacts;
            List<RawContact> updatedContacts;

            // Use the account manager to request the AuthToken we'll need
            // to talk to our sample server.  If we don't have an AuthToken
            // yet, this could involve包含 a round-trip来回,往返 to the server to request
            // and AuthToken.
            // 使用账号管理去请求AuthToken，我们需要跟例子服务器交流。
            // 如果我们还没有AuthToken，那么可能包含一个来回跟服务器去请求AuthToken。
            final String authtoken = mAccountManager.blockingGetAuthToken(account,
                    Constants.AUTHTOKEN_TYPE, NOTIFY_AUTH_FAILURE);

            // Make sure that the sample group exists
            final long groupId = ContactManager.ensureSampleGroupExists(mContext, account);

            // Find the local 'dirty' contacts that we need to tell the server about...
            // Find the local users that need to be sync'd to the server...
            dirtyContacts = ContactManager.getDirtyContacts(mContext, account);

            // Send the dirty contacts to the server, and retrieve取回 the server-side changes
            updatedContacts = NetworkUtilities.syncContacts(account, authtoken,
                    lastSyncMarker, dirtyContacts);

            // Update the local contacts database with the changes. updateContacts()
            // returns a syncState value that indicates表明 the high-water-mark for
            // the changes we received.
            Log.d(TAG, "Calling contactManager's sync contacts");
            long newSyncState = ContactManager.updateContacts(mContext,
                    account.name,
                    updatedContacts,
                    groupId,
                    lastSyncMarker);

            // This is a demo of how you can update IM-style status messages
            // for contacts on the client. This probably won't apply to
            // 2-way contact sync providers - it's more likely that one-way
            // sync providers (IM clients, social networking apps, etc) would
            // use this feature.
            // 演示了如何更新IM-style状态的消息，for客户端的联系人。
            // 或许不会请求双路联系人同步providers -  这更像是单路同步providers
            // (即时通讯客户端, 社交网络apps，等等) 会用到这个新特性。
            ContactManager.updateStatusMessages(mContext, updatedContacts);

            // Save off the new sync marker. On our next sync, we only want to receive
            // contacts that have changed since this sync...
            // 保存这个新的同步标记。当我们下次再同步的时候，我们只需要接收
            // 自从这次同步后改变的联系人就行了。
            setServerSyncMarker(account, newSyncState);

            // 如果还有需要同步的联系人，那么清除同步标记
            if (dirtyContacts.size() > 0) {
                ContactManager.clearSyncFlags(mContext, dirtyContacts);
            }

        } catch (final AuthenticatorException e) {
            Log.e(TAG, "AuthenticatorException", e);
            syncResult.stats.numParseExceptions++;
        } catch (final OperationCanceledException e) {
            Log.e(TAG, "OperationCanceledExcetpion", e);
        } catch (final IOException e) {
            Log.e(TAG, "IOException", e);
            syncResult.stats.numIoExceptions++;
        } catch (final AuthenticationException e) {
            Log.e(TAG, "AuthenticationException", e);
            syncResult.stats.numAuthExceptions++;
        } catch (final ParseException e) {
            Log.e(TAG, "ParseException", e);
            syncResult.stats.numParseExceptions++;
        } catch (final JSONException e) {
            Log.e(TAG, "JSONException", e);
            syncResult.stats.numParseExceptions++;
        }
    }

    /**
     * This helper function fetches the last known high-water-mark
     * we received from the server - or 0 if we've never synced.
     * 从服务器上获取水位线数值，如果是0就说明从来没同步过。
     * @param account the account we're syncing
     * @return the change high-water-mark
     */
    private long getServerSyncMarker(Account account) {
        String markerString = mAccountManager.getUserData(account, SYNC_MARKER_KEY);
        if (!TextUtils.isEmpty(markerString)) {
            return Long.parseLong(markerString);
        }
        return 0;
    }

    /**
     * Save off the high-water-mark we receive back from the server.
     * 保存这个高水位线，我们从服务器上获得的
     * @param account The account we're syncing
     * @param marker The high-water-mark we want to save.
     *               到底是个什么呢？
     */
    private void setServerSyncMarker(Account account, long marker) {
        mAccountManager.setUserData(account, SYNC_MARKER_KEY, Long.toString(marker));
    }
}

