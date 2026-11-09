# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users
Ela and Johann, who cook together at home. Ela owns the recipe collection and the account with full access;
the two of them plan their week and shop from it. A few invited guests (friends and family) log in to browse
recipes, search, filter and comment. They can't edit anything or see the planner.

## Product Purpose
Ela's recipes are scattered across languages (Polish, German, Spanish, English) and sources. Deliciousnessness
puts them all in one place, sortable by category, season and ingredient, and plans the week's cooking from them.
Success: finding "what can we cook with this" in seconds, whatever language the recipe was written in.

## Positioning
Search an ingredient in any language and get every recipe that uses it: `cebula` finds the German recipe that
says `Zwiebel`. Each recipe line links to one language-neutral ingredient (4,900+ seeded from Open Food Facts
with EN/PL/DE/ES names), so the recipe keeps its original wording.

## Operating Context
Used at home, on a phone or laptop in the kitchen and while planning the week. A weekly planner with two columns
(Ela, Johann), per-day nutrition, and a shopping list built from the plan. Recipes come in from cookbooks,
websites and family, in whatever language they were found in.

## Capabilities and Constraints
- Private, invite-only website at deliciousnessness.com, hosted on a Raspberry Pi; kept out of search engines.
- Spring Boot + Thymeleaf server-rendered pages, plain CSS and JS, no frontend build step.
- Roles: the owner (full access) and guests (read and comment). Recipes in the `private` category are owner-only.
- Email notifications (new recipe, season change, comment replies) for people who opt in.

## Brand Commitments
- Name: Deliciousnessness (one word). The logo carries the full name as a wordmark plus a mark that works alone
  as the favicon.
- The logo fits the site's existing look rather than starting a new identity.

## Evidence on Hand
- Real recipes in the live database (about 260), with photos for most.
- Screens: `docs/recipe-list.png`, design mockups in `design/`.
- No logo, favicon, testimonials or press exist yet. Do not invent them.

## Product Principles
1. One kitchen, many languages: the language a recipe was written in never gets in the way.
2. Personal, not public: built for two people and their guests, never for strangers or search engines.
3. Honest numbers: nutrition and amounts show what is known and say what is missing.
4. Planning serves cooking: the planner and shopping list exist to get food on the table, not to track people.
