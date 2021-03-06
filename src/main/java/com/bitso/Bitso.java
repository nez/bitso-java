package com.bitso;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HttpsURLConnection;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.experimental.theories.Theories;

import com.bitso.exchange.BookInfo;
import com.bitso.helpers.Helpers;
import com.bitso.http.BlockingHttpClient;

public class Bitso {
    private static final String BITSO_BASE_URL_PRODUCTION = "https://bitso.com";
    private static final String BITSO_BASE_URL_DEV = "https://dev.bitso.com";

    public static long THROTTLE_MS = 1000;

    private String key;
    private String secret;
    private boolean log;
    private String baseUrl;

    private BlockingHttpClient client = new BlockingHttpClient(false, THROTTLE_MS);

    private static enum CURRENCY_WITHDRAWALS {
        BITCOIN_WITHDRAWAL, ETHER_WITHDRAWAL;

        public String toString() {
            return this.name().toLowerCase();
        }
    }

    public Bitso(String key, String secret) {
        this(key, secret, 0);
    }

    public Bitso(String key, String secret, int retries) {
        this(key, secret, retries, true);
    }

    public Bitso(String key, String secret, int retries, boolean log) {
        this(key, secret, retries, log, true);
    }

    public Bitso(String key, String secret, int retries, boolean log, boolean production) {
        this.key = key;
        this.secret = secret;
        this.log = log;
        this.baseUrl = production ? BITSO_BASE_URL_PRODUCTION : BITSO_BASE_URL_DEV;
    }

    public String getKey() {
        return key;
    }

    public String getSecret() {
        return secret;
    }

    public void setLog(boolean log) {
        this.log = log;
    }

    private void logError(String error) {
        if (log) {
            System.err.println(error);
        }
    }

    private void log(String msg) {
        if (log) {
            System.out.println(msg);
        }
    }

    // Public Functions
    public ArrayList<BookInfo> availableBooks() {
        String json = sendGet("/api/v3/available_books");
        JSONObject o = Helpers.parseJson(json);
        if (o == null || o.has("error")) {
            logError("Unable to get Bitso Ticker: " + json);
            return null;
        }
        ArrayList<BookInfo> books = new ArrayList<BookInfo>();
        JSONArray arr = o.getJSONArray("payload");
        for (int i = 0; i < arr.length(); i++) {
            books.add(new BookInfo(arr.getJSONObject(i)));
        }
        return books;
    }

    public BitsoTicker[] getTicker() {
        String json = sendGet("/api/v3/ticker");
        JSONObject o = Helpers.parseJson(json);
        if (o == null || o.has("error")) {
            logError("Unable to get Bitso Ticker: " + json);
            return null;
        }

        if (o.has("error")) {
            return null;
        }

        JSONArray arrayTickers = o.getJSONArray("payload");
        int totalTickers = arrayTickers.length();

        BitsoTicker[] tickers = new BitsoTicker[totalTickers];
        for (int i = 0; i < totalTickers; i++) {
            tickers[i] = new BitsoTicker(arrayTickers.getJSONObject(i));
        }

        return tickers;
    }

    public BitsoOrderBook getOrderBook(BitsoBook book, boolean... aggregate) {
        String request = "/api/v3/order_book?book=" + book.toString();
        if (aggregate != null && aggregate.length == 1) {
            if (aggregate[0]) {
                request += "&aggregate=true";
            } else {
                request += "&aggregate=false";
            }
        }
        String json = sendGet(request);
        JSONObject o = Helpers.parseJson(json);
        if (o == null) {
            logError("Unable to get Bitso Order Book");
            return null;
        }
        if (o.has("payload")) {
            return new BitsoOrderBook(o.getJSONObject("payload"));
        }
        return null;
    }

    public BitsoTransactions getTransactions(BitsoBook book) {
        String json = sendGet(baseUrl + "trades?book=" + book.toString());
        JSONArray a = Helpers.parseJsonArray(json);
        if (a == null) {
            logError("Unable to get Bitso Transactions");
            return null;
        }
        return new BitsoTransactions(a);
    }

    // Private Functions
    public BitsoAccountStatus getUserAccountStatus() {
        String json = sendBitsoGet("/api/v3/account_status");
        JSONObject o = Helpers.parseJson(json);
        if (o == null || o.has("error")) {
            logError("Error getting Bitso Account Status: " + json);
            return null;
        }
        if (o.has("payload")) {
            JSONObject payload = o.getJSONObject("payload");
            return new BitsoAccountStatus(payload);
        }
        return null;
    }

    public BitsoBalance getUserAccountBalance() {
        String json = sendBitsoGet("/api/v3/balance");
        JSONObject o = Helpers.parseJson(json);
        if (o == null || o.has("error")) {
            logError("Error getting account balance: " + json);
            return null;
        }
        return new BitsoBalance(o);
    }

    public BitsoFee getUserFees() {
        String json = sendBitsoGet("/api/v3/fees");
        JSONObject o = Helpers.parseJson(json);
        if (o == null || o.has("error")) {
            logError("Error getting user fees: " + json);
            return null;
        }
        return new BitsoFee(o);
    }

    public BitsoOperation[] getUserLedger(String specificOperation, String queryParameters) {
        String request = "/api/v3/ledger";

        if (specificOperation != null && specificOperation.length() > 0) {
            request += "/" + specificOperation;
        }

        if (queryParameters != null && queryParameters.length() > 0) {
            request += "?" + queryParameters;
        }

        log(request);
        String json = sendBitsoGet(request);
        JSONObject o = Helpers.parseJson(json);
        if (o == null || o.has("error")) {
            logError("Error getting user ledgers: " + json);
            return null;
        }
        if (o.has("payload")) {
            JSONArray payload = o.getJSONArray("payload");
            int totalElements = payload.length();
            BitsoOperation[] operations = new BitsoOperation[totalElements];
            for (int i = 0; i < totalElements; i++) {
                operations[i] = new BitsoOperation(payload.getJSONObject(i));
            }
            return operations;
        }
        return null;
    }

    public BitsoWithdrawal[] getUserWithdrawals(String... withdrawalsIds) {
        String request = "/api/v3/withdrawals/";
        request += buildDynamicURLParameters(withdrawalsIds);
        log(request);
        String json = sendBitsoGet(request);
        JSONObject o = Helpers.parseJson(json);
        if (o == null || o.has("error")) {
            logError("Error getting user withdrawals: " + json);
            return null;
        }
        if (o.has("payload")) {
            JSONArray payload = o.getJSONArray("payload");
            int totalElements = payload.length();
            BitsoWithdrawal[] withdrawals = new BitsoWithdrawal[totalElements];
            for (int i = 0; i < totalElements; i++) {
                withdrawals[i] = new BitsoWithdrawal(payload.getJSONObject(i));
            }
            return withdrawals;
        }
        return null;
    }

    public BitsoFunding[] getUserFundings(String... fundingsIds) {
        String request = "/api/v3/fundings/";
        request += buildDynamicURLParameters(fundingsIds);
        log(request);
        String json = sendBitsoGet(request);
        JSONObject o = Helpers.parseJson(json);
        if (o == null || o.has("error")) {
            logError("Error getting user fundings: " + json);
            return null;
        }
        if (o.has("payload")) {
            JSONArray payload = o.getJSONArray("payload");
            int totalElements = payload.length();
            BitsoFunding[] fundings = new BitsoFunding[totalElements];
            for (int i = 0; i < totalElements; i++) {
                fundings[i] = new BitsoFunding(payload.getJSONObject(i));
            }
            return fundings;
        }
        return null;
    }

    public BitsoTrade[] getUserTrades(String... tradesIds) {
        String request = "/api/v3/user_trades/";
        request += buildDynamicURLParameters(tradesIds);
        log(request);
        String json = sendBitsoGet(request);
        JSONObject o = Helpers.parseJson(json);
        if (o == null || o.has("error")) {
            logError("Error getting user trades: " + json);
            return null;
        }
        if (o.has("payload")) {
            JSONArray payload = o.getJSONArray("payload");
            int totalElements = payload.length();
            BitsoTrade[] trades = new BitsoTrade[totalElements];
            for (int i = 0; i < totalElements; i++) {
                trades[i] = new BitsoTrade(payload.getJSONObject(i));
            }
            return trades;
        }
        return null;
    }

    public BitsoOrder[] getOpenOrders() {
        String request = "/api/v3/open_orders?book=btc_mxn";
        String json = sendBitsoGet(request);
        JSONObject o = Helpers.parseJson(json);
        if (o == null || o.has("error")) {
            logError("Error in Open Orders: " + json);
            return null;
        }
        if (o.has("payload")) {
            JSONArray payload = o.getJSONArray("payload");
            int totalElements = payload.length();
            BitsoOrder[] orders = new BitsoOrder[totalElements];
            for (int i = 0; i < totalElements; i++) {
                orders[i] = new BitsoOrder(payload.getJSONObject(i));
            }
            return orders;
        }
        return null;
    }

    public BitsoOrder[] lookupOrders(String... ordersId) {
        String request = "/api/v3/orders/";
        request += buildDynamicURLParameters(ordersId);
        log(request);
        String json = sendBitsoGet(request);
        JSONObject o = Helpers.parseJson(json);
        if (o == null || o.has("error")) {
            logError("Error in lookupOrders: " + json);
            return null;
        }
        if (o.has("payload")) {
            JSONArray payload = o.getJSONArray("payload");
            int totalElements = payload.length();
            BitsoOrder[] orders = new BitsoOrder[totalElements];
            for (int i = 0; i < totalElements; i++) {
                orders[i] = new BitsoOrder(payload.getJSONObject(i));
            }
            return orders;
        }
        return null;
    }

    public String placeOrder(BitsoBook book, BitsoOrder.SIDE side, BitsoOrder.TYPE type, BigDecimal major,
            BigDecimal minor, BigDecimal price) {
        String request = "/api/v3/orders";
        JSONObject parameters = new JSONObject();

        if ((major != null && minor != null) || (major == null && minor == null)) {
            log("An order should be specified in terms of major or minor, never both.");
            return null;
        }

        if ((type.compareTo(BitsoOrder.TYPE.LIMIT) == 0) && (price != null)) {
            parameters.put("price", price.toString());
        } else {
            log("Price must be specified on limit orders.");
            return null;
        }

        parameters.put("book", book.toString());
        parameters.put("side", side.toString());
        parameters.put("type", type.toString());

        if (major != null) {
            parameters.put("major", major.toString());
        } else {
            parameters.put("minor", minor.toString());
        }
        String json = sendBitsoPost(request, parameters);
        JSONObject o = Helpers.parseJson(json);
        if (o == null || o.has("error")) {
            logError("Error placing an order: " + json);
            return null;
        }
        if (o.has("payload")) {
            JSONObject payload = o.getJSONObject("payload");
            return Helpers.getString(payload, "oid");
        }
        return null;
    }

    public String[] cancelOrder(String... orders) {
        String request = "/api/v3/orders/";
        request += buildDynamicURLParameters(orders);
        log(request);
        String json = sendBitsoDelete(request);
        JSONObject o = Helpers.parseJson(json);
        if (o == null || o.has("error")) {
            logError("Error cancelling orders: " + json);
            return null;
        }
        return Helpers.parseJSONArray(o.getJSONArray("payload"));
    }

    public Map<String, String> fundingDestination(String fundCurrency) {
        String request = "/api/v3/funding_destination?" + "fund_currency=" + fundCurrency;
        log(request);
        String json = sendBitsoGet(request);
        JSONObject o = Helpers.parseJson(json);
        if (o == null || o.has("error")) {
            logError("Error getting funding destination: " + json);
            return null;
        }
        if (o.has("payload")) {
            JSONObject payload = o.getJSONObject("payload");
            Map<String, String> fundingDestination = new HashMap<String, String>();
            fundingDestination.put("accountIdentifierName",
                    Helpers.getString(payload, "account_identifier_name"));
            fundingDestination.put("accountIdentifier", Helpers.getString(payload, "account_identifier"));
            return fundingDestination;
        }
        return null;
    }

    public BitsoWithdrawal bitcoinWithdrawal(BigDecimal amount, String address) {
        return currencyWithdrawal(CURRENCY_WITHDRAWALS.BITCOIN_WITHDRAWAL, amount, address);
    }

    public BitsoWithdrawal etherWithdrawal(BigDecimal amount, String address) {
        return currencyWithdrawal(CURRENCY_WITHDRAWALS.ETHER_WITHDRAWAL, amount, address);
    }

    public BitsoWithdrawal speiWithdrawal(BigDecimal amount, String recipientGivenNames,
            String recipientFamilyNames, String clabe, String notesReference, String numericReference) {
        String request = "/api/v3/spei_withdrawal";
        JSONObject parameters = new JSONObject();
        parameters.put("amount", amount.toString());
        parameters.put("recipient_given_names", recipientGivenNames);
        parameters.put("recipient_family_names", recipientFamilyNames);
        parameters.put("clabe", clabe);
        parameters.put("notes_ref", notesReference);
        parameters.put("numeric_ref", numericReference);
        String json = sendBitsoPost(request, parameters);
        JSONObject o = Helpers.parseJson(json);
        if (o == null || o.has("error")) {
            logError("Error executing withdrawal" + json);
            return null;
        }
        if (o.has("payload")) {
            JSONObject payload = o.getJSONObject("payload");
            return new BitsoWithdrawal(payload);
        }
        return null;
    }

    public Map<String, String> getBanks() {
        String request = "/api/v3/mx_bank_codes";
        log(request);
        String json = sendBitsoGet(request);
        JSONObject o = Helpers.parseJson(json);
        if (o == null || o.has("error")) {
            logError("Error in lookupOrders: " + json);
            return null;
        }
        if (o.has("payload")) {
            Map<String, String> banks = new HashMap<String, String>();
            JSONArray payload = o.getJSONArray("payload");
            String currentBankCode = "";
            String currentBankName = "";
            JSONObject currentJSON = null;
            ;
            int totalElements = payload.length();
            for (int i = 0; i < totalElements; i++) {
                currentJSON = payload.getJSONObject(i);
                currentBankCode = Helpers.getString(currentJSON, "code");
                currentBankName = Helpers.getString(currentJSON, "name");
                banks.put(currentBankCode, currentBankName);
            }
            return banks;
        }
        return null;
    }

    public BitsoWithdrawal debitCardWithdrawal(BigDecimal amount, String recipientGivenNames,
            String recipientFamilyNames, String cardNumber, String bankCode) {
        String request = "/api/v3/debit_card_withdrawal";
        JSONObject parameters = new JSONObject();
        parameters.put("amount", amount.toString());
        parameters.put("recipient_given_names", recipientGivenNames);
        parameters.put("recipient_family_names", recipientFamilyNames);
        parameters.put("card_number", cardNumber);
        parameters.put("bank_code", bankCode);
        String json = sendBitsoPost(request, parameters);
        JSONObject o = Helpers.parseJson(json);
        if (o == null || o.has("error")) {
            logError("Error executing withdrawal" + json);
            return null;
        }
        if (o.has("payload")) {
            JSONObject payload = o.getJSONObject("payload");
            return new BitsoWithdrawal(payload);
        }
        return null;
    }

    public BitsoWithdrawal phoneWithdrawal(BigDecimal amount, String recipientGivenNames,
            String recipientFamilyNames, String phoneNumber, String bankCode) {
        String request = "/api/v3/phone_withdrawal";
        JSONObject parameters = new JSONObject();
        parameters.put("amount", amount.toString());
        parameters.put("recipient_given_names", recipientGivenNames);
        parameters.put("recipient_family_names", recipientFamilyNames);
        parameters.put("phone_number", phoneNumber);
        parameters.put("bank_code", bankCode);
        String json = sendBitsoPost(request, parameters);
        JSONObject o = Helpers.parseJson(json);
        if (o == null || o.has("error")) {
            logError("Error executing withdrawal" + json);
            return null;
        }
        if (o.has("payload")) {
            JSONObject payload = o.getJSONObject("payload");
            return new BitsoWithdrawal(payload);
        }
        return null;
    }

    private BitsoWithdrawal currencyWithdrawal(CURRENCY_WITHDRAWALS withdrawal, BigDecimal amount,
            String address) {
        String request = "/api/v3/" + withdrawal.toString();
        JSONObject parameters = new JSONObject();
        parameters.put("amount", amount.toString());
        parameters.put("address", address);
        String json = sendBitsoPost(request, parameters);
        JSONObject o = Helpers.parseJson(json);
        if (o == null || o.has("error")) {
            logError("Error executing withdrawal: " + json);
            return null;
        }
        if (o.has("payload")) {
            JSONObject payload = o.getJSONObject("payload");
            return new BitsoWithdrawal(payload);
        }
        return null;
    }

    public String getDepositAddress() {
        return quoteEliminator(sendBitsoPost(baseUrl + "bitcoin_deposit_address"));
    }

    public BitsoTransfer getTransferStatus(String transferId) {
        String ret = sendGet(baseUrl + "transfer/" + transferId);
        JSONObject o = Helpers.parseJson(ret);
        if (o == null || o.has("error")) {
            logError("Unable to get transfer status: " + ret);
            return null;
        }
        return new BitsoTransfer(o);
    }

    private String quoteEliminator(String input) {
        if (input == null) {
            logError("input to quoteEliminator cannot be null");
            return null;
        }
        int length = input.length();
        if (input.charAt(0) != '"' || input.charAt(length - 1) != '"') {
            logError("invalid input to quoteEliminator: " + input);
            return null;
        }
        return input.substring(1, length - 1);
    }

    private String buildBitsoAuthHeader(String requestPath, String httpMethod, String apiKey, String secret) {
        long nonce = System.currentTimeMillis();
        byte[] secretBytes = secret.getBytes();
        byte[] arrayOfByte = null;
        String signature = null;
        BigInteger bigInteger = null;
        Mac mac = null;

        String message = nonce + httpMethod + requestPath;
        SecretKeySpec secretKeySpec = new SecretKeySpec(secretBytes, "HmacSHA256");
        try {
            mac = Mac.getInstance("HmacSHA256");
            mac.init(secretKeySpec);
            arrayOfByte = mac.doFinal(message.getBytes());
            bigInteger = new BigInteger(1, arrayOfByte);
            signature = String.format("%0" + (arrayOfByte.length << 1) + "x", new Object[] { bigInteger });
            return String.format("Bitso %s:%s:%s", apiKey, nonce, signature);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static Entry<String, String> buildBitsoAuthHeader(String secretKey, String publicKey, long nonce,
            String httpMethod, String requestPath, String jsonPayload) {
        if (jsonPayload == null) jsonPayload = "";
        String message = String.valueOf(nonce) + httpMethod + requestPath + jsonPayload;
        String signature = "";
        byte[] secretBytes = secretKey.getBytes();
        SecretKeySpec localMac = new SecretKeySpec(secretBytes, "HmacSHA256");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(localMac);
            // Compute the hmac on input data bytes
            byte[] arrayOfByte = mac.doFinal(message.getBytes());
            BigInteger localBigInteger = new BigInteger(1, arrayOfByte);
            signature = String.format("%0" + (arrayOfByte.length << 1) + "x",
                    new Object[] { localBigInteger });
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
        String authHeader = String.format("Bitso %s:%s:%s", publicKey, nonce, signature);
        Entry<String, String> entry = new AbstractMap.SimpleEntry<String, String>("Authorization",
                authHeader);
        return entry;
    }

    public String sendGet(String requestedURL) {
        try {
            URL url = new URL(baseUrl + requestedURL);
            HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.setRequestProperty("User-Agent", "Android");

            if (con.getResponseCode() == 200) {
                int responseCode = con.getResponseCode();
                return convertInputStreamToString(con.getInputStream());
            }
        } catch (MalformedURLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }

    private String sendBitsoHttpRequest(String requestPath, String method) {
        String response = null;
        String requestURL = baseUrl + requestPath;

        try {
            URL url = new URL(requestURL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.addRequestProperty("Authorization",
                    buildBitsoAuthHeader(requestPath, "GET", key, secret));
            connection.setRequestProperty("User-Agent", "Bitso-java-api");
            connection.setRequestMethod(method);
            if (connection.getResponseCode() == 200) {
                InputStream inputStream = new BufferedInputStream(connection.getInputStream());
                response = convertInputStreamToString(inputStream);
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (ProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return response;
    }

    private String sendBitsoGet(String requestPath) {
        return sendBitsoHttpRequest(requestPath, "GET");
    }

    private String sendBitsoDelete(String requestPath) {
        long nonce = System.currentTimeMillis();
        Entry<String, String> authHeader = buildBitsoAuthHeader(secret, key, nonce, "DELETE", requestPath,
                null);
        HashMap<String, String> headers = new HashMap<String, String>();
        headers.put("Content-Type", "application/json");
        headers.put(authHeader.getKey(), authHeader.getValue());
        try {
            return client.sendDelete(baseUrl + requestPath, headers);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private String sendBitsoPost(String url) {
        return sendBitsoPost(url, null);
    }

    private String sendBitsoPost(String requestPath, JSONObject jsonPayload) {
        long nonce = System.currentTimeMillis();
        String jsonString = "";
        if (jsonPayload != null) {
            jsonString = jsonPayload.toString();
        }
        Entry<String, String> header = buildBitsoAuthHeader(secret, key, nonce, "POST", requestPath,
                jsonString);
        HashMap<String, String> headers = new HashMap<String, String>();
        headers.put("Content-Type", "application/json");
        headers.put(header.getKey(), header.getValue());
        try {
            return client.sendPost(baseUrl + requestPath, jsonString, headers);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private String buildDynamicURLParameters(String[] elements) {
        int totalIds = elements.length;
        String parameters = "";
        if (totalIds > 0) {
            for (int i = 0; i < totalIds - 1; i++) {
                parameters += elements[i] + "-";
            }
            parameters += elements[totalIds - 1];
            ;
        }
        return parameters;
    }

    private static String convertInputStreamToString(InputStream inputStream) {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder stringBuilder = new StringBuilder();
        String line = null;
        try {
            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return stringBuilder.toString();
    }
}
