package net.minecraft.util;

/**
 * 1.9+ EnumActionResult 的 1.7.10 回填。
 * 用于让 1.8+ 风格的方法签名可以编译；1.7.10 原生物品回调仍是 boolean。
 */
public enum EnumActionResult
{
	SUCCESS,
	PASS,
	FAIL;
}
