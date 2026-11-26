package com.trevorplus;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared constants. All of the mod's work happens client-side, in TrevorplusClient. */
public class Trevorplus implements ModInitializer {
	public static final String MOD_ID = "trevorplus";

	public static final Logger LOGGER = LoggerFactory.getLogger("Trevor+");

	@Override
	public void onInitialize() {
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
