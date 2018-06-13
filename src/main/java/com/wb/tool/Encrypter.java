package com.wb.tool;

import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;

import com.wb.util.StringUtil;

/**
 * 提供常用的加密/解密方法。
 */
public class Encrypter {
	/**
	 * DES字符常量。
	 */
	private static final String des = "DES";
	/**
	 * MD5 16进制转码时的映射字母表。
	 */
	private static final String keyMap = "C2E8D9A3B5F14607";

	/**
	 * 使用密钥通过DES算法加密指定的文本。
	 * 
	 * @param text
	 *            需要加密的文本。
	 * @param key
	 *            8位字节的密钥。
	 * @return 加密后的文本。
	 * @throws Exception
	 *             加密过程发生异常。
	 */
	public static String encrypt(String text, String key) throws Exception {
		return StringUtil.encodeBase64(encrypt(text.getBytes("utf-8"), key));
	}

	/**
	 * 使用密钥解密通过DES算法加密的文本。
	 * 
	 * @param text
	 *            需要解密的文本。
	 * @param key
	 *            8位字节的密钥。
	 * @return 解密后的文本。
	 * @throws Exception
	 *             解密过程发生异常。
	 */
	public static String decrypt(String text, String key) throws Exception {
		return new String(decrypt(StringUtil.decodeBase64(text), key), "utf-8");
	}

	/**
	 * 使用密钥通过DES算法加密指定的字节。
	 * 
	 * @param bytes
	 *            需要加密的字节。
	 * @param key
	 *            8位字节的密钥。
	 * @return 加密后的字节。
	 * @throws Exception
	 *             加密过程发生异常。
	 */
	public static byte[] encrypt(byte[] bytes, String key) throws Exception {
		byte[] keyBytes = key.getBytes("utf-8");
		SecureRandom sr = new SecureRandom();
		DESKeySpec dks = new DESKeySpec(keyBytes);
		SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(des);
		SecretKey securekey = keyFactory.generateSecret(dks);
		Cipher cipher = Cipher.getInstance(des);
		cipher.init(Cipher.ENCRYPT_MODE, securekey, sr);
		return cipher.doFinal(bytes);
	}

	/**
	 * 使用密钥解密通过DES算法加密的字节。
	 * 
	 * @param bytes
	 *            需要解密的字节。
	 * @param key
	 *            8位字节的密钥。
	 * @return 解密后的字节。
	 * @throws Exception
	 *             解密过程发生异常。
	 */
	public static byte[] decrypt(byte[] bytes, String key) throws Exception {
		byte[] keyBytes = key.getBytes("utf-8");
		SecureRandom sr = new SecureRandom();
		DESKeySpec dks = new DESKeySpec(keyBytes);
		SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(des);
		SecretKey securekey = keyFactory.generateSecret(dks);
		Cipher cipher = Cipher.getInstance(des);
		cipher.init(Cipher.DECRYPT_MODE, securekey, sr);
		return cipher.doFinal(bytes);
	}

	/**
	 * 对指定文本使用MD5算法进行加密，使用此加密算法获得的值不可解密。
	 * 
	 * @param bytes
	 *            需要加密的字节。
	 * @return 32位16进制字符组成的密码。
	 * @throws Exception
	 *             加密过程中发生异常。
	 */
	public static String getMD5(String text) throws Exception {
		return getMD5(text.getBytes("utf-8"));
	}

	/**
	 * 对指定字节使用MD5算法进行加密，使用此加密算法获得的值不可解密。
	 * 
	 * @param bytes
	 *            需要加密的字节。
	 * @return 32位16进制字符组成的密码。
	 * @throws Exception
	 *             加密过程中发生异常。
	 */
	public static String getMD5(byte[] bytes) throws Exception {
		MessageDigest md = MessageDigest.getInstance("MD5");
		md.update(bytes);
		byte bt, codes[] = md.digest();
		char str[] = new char[32];
		int i, j = 0;
		for (i = 0; i < 16; i++) {
			bt = codes[i];
			str[j++] = keyMap.charAt(bt >>> 4 & 0xf);
			str[j++] = keyMap.charAt(bt & 0xf);
		}
		return new String(str);
	}
}