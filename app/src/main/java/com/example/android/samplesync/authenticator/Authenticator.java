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

package com.example.android.samplesync.authenticator;

import com.example.android.samplesync.Constants;
import com.example.android.samplesync.client.NetworkUtilities;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

/**
 * This class is an implementation of AbstractAccountAuthenticator for
 * 这个Class实现了抽象类AbstractAccountAuthenticator。
 * authenticating鉴定 accounts in the com.example.android.samplesync domain. The
 * 验证账号在这个例子里：com.example.android.samplesync
 * interesting thing that this class demonstrates is the use of authTokens as
 * 有趣的是这个Class Demo 使用了authTokens(令牌)
 * part of the authentication美 [ɔˌθentɪ'keɪʃn]验证 process. In the account setup UI, the user enters
 * 作为验证过程的一部分，在账号的UI步骤，用户输入
 * their username and password. But for our subsequent后续的 calls off to the service
 * 他们的用户名和密码。但是为了后续的服务器同步调用。off to 要去（干什么）。
 * for syncing, we want to use an authtoken instead - so we're not continually（持续的）
 * 我们想要用authtoken代替-所以我们不用持续不断的
 * sending the password over the wire. getAuthToken() will be called when
 * 发送密码在线路上。getAuthToken()会被调用当
 * SyncAdapter calls AccountManager.blockingGetAuthToken(). When we get called,
 * SyncAdapter 调用 AccountManager.blockingGetAuthToken().一旦被调用，
 * we need to return the appropriate authToken for the specified account. If we
 * 需要return合适的authToken为这个指定的账号。如果我们
 * already have an authToken stored in the account, we return that authToken. If
 * 在这个account里面已经保存了authToken，就直接return这个authToken。
 * we don't, but we do have a username and password, then we'll attempt to talk
 * 否则，如果我们有用户名和密码，那么我们就尝试
 * to the sample service to fetch an authToken. If that fails (or we didn't have
 * 跟示例服务去获取authToken.如果失败了（或者我们根本没有用户名密码）
 * a username/password), then we need to prompt提示 the user - so we create an
 * 那么我们需要提示用户-so我们创建了AuthenticatorActivity
 * AuthenticatorActivity intent and return that. That will display the dialog
 * 意图并且return它。将会显示一个对话框
 * that prompts the user for their login information.
 * 提示用户输入他们的登录信息。
 */
class Authenticator extends AbstractAccountAuthenticator {

    /** The tag used to log to adb console. **/
    private static final String TAG = "Authenticator";

    // Authentication Service context
    private final Context mContext;

    public Authenticator(Context context) {
        super(context);
        mContext = context;
    }

    @Override
    public Bundle addAccount(AccountAuthenticatorResponse response, String accountType,
            String authTokenType, String[] requiredFeatures, Bundle options) {
        Log.v(TAG, "addAccount()");
        final Intent intent = new Intent(mContext, AuthenticatorActivity.class);
        intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);
        final Bundle bundle = new Bundle();
        bundle.putParcelable(AccountManager.KEY_INTENT, intent);
        return bundle;
    }

    @Override
    public Bundle confirmCredentials(
            AccountAuthenticatorResponse response, Account account, Bundle options) {
        Log.v(TAG, "confirmCredentials()");
        return null;
    }

    @Override
    public Bundle editProperties(AccountAuthenticatorResponse response, String accountType) {
        Log.v(TAG, "editProperties()");
        throw new UnsupportedOperationException();
    }

    @Override
    public Bundle getAuthToken(AccountAuthenticatorResponse response, Account account,
            String authTokenType, Bundle loginOptions) throws NetworkErrorException {
        Log.v(TAG, "getAuthToken()");

        // If the caller requested an authToken type we don't support, then
        // return an error
        // 如果调用者请求的authToken类型我们不能提供，那么return an error.
        if (!authTokenType.equals(Constants.AUTHTOKEN_TYPE)) {
            final Bundle result = new Bundle();
            result.putString(AccountManager.KEY_ERROR_MESSAGE, "invalid authTokenType");
            return result;
        }

        // Extract the username and password from the Account Manager, and ask
        // the server for an appropriate AuthToken.
        // 从Account Manager获取用户名密码，然后请求服务器得到一个合适的AuthToken.
        final AccountManager am = AccountManager.get(mContext);
        final String password = am.getPassword(account);
        if (password != null) {
            final String authToken = NetworkUtilities.authenticate(account.name, password);
            if (!TextUtils.isEmpty(authToken)) {
                final Bundle result = new Bundle();
                result.putString(AccountManager.KEY_ACCOUNT_NAME, account.name);
                result.putString(AccountManager.KEY_ACCOUNT_TYPE, Constants.ACCOUNT_TYPE);
                result.putString(AccountManager.KEY_AUTHTOKEN, authToken);
                return result;
            }
        }

        // If we get here, then we couldn't access the user's password - so we
        // need to re-prompt them for their credentials证书. We do that by creating
        // an intent to display our AuthenticatorActivity panel.
        // 如果跑到这里了，说明用户密码没有验证通过-所以需要再次提示获取证书
        // 我们创建一个intent显示AuthenticatorActivity对话框面板.
        final Intent intent = new Intent(mContext, AuthenticatorActivity.class);
        intent.putExtra(AuthenticatorActivity.PARAM_USERNAME, account.name);
        intent.putExtra(AuthenticatorActivity.PARAM_AUTHTOKEN_TYPE, authTokenType);
        intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);
        final Bundle bundle = new Bundle();
        bundle.putParcelable(AccountManager.KEY_INTENT, intent);
        return bundle;
    }

    @Override
    public String getAuthTokenLabel(String authTokenType) {
        // null means we don't support multiple authToken types
        // null 表示我们不支持多authToken类型
        Log.v(TAG, "getAuthTokenLabel()");
        return null;
    }

    @Override
    public Bundle hasFeatures(
            AccountAuthenticatorResponse response, Account account, String[] features) {
        // This call is used to query whether the Authenticator supports
        // specific features. We don't expect to get called, so we always
        // return false (no) for any queries.
        // 这个回调被用于查询，Authenticator是否支持指定的features.
        // 我们不期望被调用，所以一直return false 也就是no，for任何查询
        Log.v(TAG, "hasFeatures()");
        final Bundle result = new Bundle();
        result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, false);
        return result;
    }

    @Override
    public Bundle updateCredentials(AccountAuthenticatorResponse response, Account account,
            String authTokenType, Bundle loginOptions) {
        Log.v(TAG, "updateCredentials()");
        return null;
    }
}
