package com.duoauth.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Locale;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.message.HeaderGroup;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Http {
    private String method;
    private String host;
    private String uri;
    private HeaderGroup headers;
    private ArrayList<NameValuePair> params;
    private Hashtable<String, Object> http_client_params;

    // Spring Logger
    private static final Logger logger = LoggerFactory.getLogger(Http.class);

    public static SimpleDateFormat RFC_2822_DATE_FORMAT
            = new SimpleDateFormat("EEE', 'dd' 'MMM' 'yyyy' 'HH:mm:ss' 'Z",
            Locale.US);

    public Http(String in_method, String in_host, String in_uri) {
        method = in_method.toUpperCase();
        host = in_host;
        uri = in_uri;

        headers = new HeaderGroup();
        addHeader("Host", host);

        params = new ArrayList<NameValuePair>();
        http_client_params = new Hashtable<String, Object>();
    }

    public Object executeRequest() throws Exception {

        JSONObject result = new JSONObject(executeRequestRaw());
        logger.debug("Execute Request Result: " +  result);
        if (! result.getString("stat").equals("OK")) {
            throw new Exception("Duo error code ("
                    + result.getInt("code")
                    + "): "
                    + result.getString("message"));
        }
        return result.get("response");
    }

    public String executeRequestRaw() throws Exception {
        String url = "https://" + host + uri;
        String queryString = createQueryString();
        logger.debug("Executing " + url + " " + queryString);
        HttpRequestBase request;
        if (method.equals("GET") || method.equals("DELETE")) {
            if (queryString.length() > 0) {
                url += "?" + queryString;
            }
        }

        if (method.equals("GET")) {
            request = new HttpGet(url);
        } else if (method.equals("POST")) { // or PUT (currently unused)
            HttpEntityEnclosingRequestBase ee_request = new HttpPost(url);
            ee_request.setEntity(new UrlEncodedFormEntity(params));
            request = ee_request;
        } else if (method.equals("DELETE")) {
            request = new HttpDelete(url);
        } else {
            throw new UnsupportedOperationException("Unsupported method: "
                    + method);
        }

        // finish and execute request
        request.setHeaders(headers.getAllHeaders());
        HttpClient httpclient = new DefaultHttpClient();
        Enumeration em=http_client_params.keys();
        while(em.hasMoreElements())
        {
            String key = (String) em.nextElement();
            Object value=(Object)http_client_params.get(key);
            httpclient.getParams().setParameter(key, value);
        }
        logger.debug("HTTP Client: " + httpclient);
        HttpResponse response = httpclient.execute(request);
        logger.debug("Response " + response);
        // parse response
        InputStream stream = response.getEntity().getContent();
        String buf = streamToString(stream);
        return buf;
    }

    public void signRequest(String ikey, String skey)
            throws UnsupportedEncodingException {
        signRequest(ikey, skey, 2);
    }

    public void signRequest(String ikey, String skey, int sig_version)
            throws UnsupportedEncodingException {
        String date = formatDate(new Date());
        String canon = canonRequest(date, sig_version);
        String sig = signHMAC(skey, canon);

        String auth = ikey + ":" + sig;
        String header = "Basic " + Base64.encodeBytes(auth.getBytes());
        addHeader("Authorization", header);
        if (sig_version == 2) {
            addHeader("Date", date);
        }
    }

    protected String signHMAC(String skey, String msg) {
        try {
            byte[] sig_bytes = Util.hmacSha1(skey.getBytes(), msg.getBytes());
            String sig = Util.bytes_to_hex(sig_bytes);
            return sig;
        } catch (Exception e) {
            return "";
        }
    }

    private synchronized String formatDate(Date date) {
        return RFC_2822_DATE_FORMAT.format(date);
    }

    public void addHeader(String name, String value) {
        headers.addHeader(new BasicHeader(name, value));
    }

    public void addParam(String name, String value) {
        params.add(new BasicNameValuePair(name, value));
    }

    public void addHttpClientParam(String name, Object value)
    {
        http_client_params.put(name, value);
    }

    public void setProxyServer(String proxy_host, int proxy_port)
    {
        // adding support for a HTTP Proxy Server
        HttpHost proxy = new HttpHost(proxy_host, proxy_port);
        addHttpClientParam(ConnRoutePNames.DEFAULT_PROXY, proxy);
    }

    protected String canonRequest(String date, int sig_version)
            throws UnsupportedEncodingException {
        String canon = "";
        if (sig_version == 2) {
            canon += date + "\n";
        }
        canon += method.toUpperCase() + "\n";
        canon += host.toLowerCase() + "\n";
        canon += uri + "\n";
        canon += createQueryString();

        return canon;
    }

    private String streamToString(InputStream stream) {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream));
        StringBuilder sb = new StringBuilder();
        String line = null;

        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                stream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return sb.toString();
    }

    private String createQueryString()
            throws UnsupportedEncodingException {
        ArrayList<String> args = new ArrayList<String>();
        ArrayList<String> keys = new ArrayList<String>();

        for (NameValuePair pair : params) {
            keys.add(pair.getName());
        }

        Collections.sort(keys);

        for (String key : keys) {
            for (NameValuePair pair : params) {
                if (key.equals(pair.getName())) {
                    String name = URLEncoder
                            .encode(pair.getName(), "UTF-8")
                            .replace("+", "%20")
                            .replace("*", "%2A")
                            .replace("%7E", "~");
                    String value = URLEncoder
                            .encode(pair.getValue(), "UTF-8")
                            .replace("+", "%20")
                            .replace("*", "%2A")
                            .replace("%7E", "~");
                    args.add(name + "=" + value);
                    break;
                }
            }
        }

        return Util.join(args.toArray(), "&");
    }
}