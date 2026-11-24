/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.service;

import com.personavault.entity.WikiPageType;

/**
 * One row of the generated wiki index.
 *
 * @param slug page slug, used as the wikilink target
 * @param pageType page type, used to group entries
 * @param title human-readable page title
 */
public record WikiIndexEntry(String slug, WikiPageType pageType, String title) {}
