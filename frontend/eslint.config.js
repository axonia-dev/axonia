import security from "eslint-plugin-security";
import simpleImportSort from "eslint-plugin-simple-import-sort";
import sonarjs from "eslint-plugin-sonarjs";
import tseslint from "typescript-eslint";
import prettier from "eslint-config-prettier";

// oxlint (see .oxlintrc.json) carries every rule it has a port for, including the
// type-aware typescript set, and runs the whole tree in ~2s. What stays here is what
// oxlint has no equivalent for: sonarjs' dataflow analysis (dead stores, unread
// collections, always-true conditions, ReDoS), the security plugin, import sorting,
// and the em-dash copy rule, which needs esquery selectors. None of these need type
// information, so this pass runs without projectService and stays cheap.
export default tseslint.config(
  {
    ignores: [
      "dist",
      "dist-ssr",
      "dist-selfhosted",
      "coverage",
      "playwright-report",
      "test-results",
    ],
  },
  {
    files: ["**/*.{ts,tsx}"],
    languageOptions: {
      parser: tseslint.parser,
      parserOptions: {
        ecmaFeatures: { jsx: true },
        // sonarjs degrades silently without type information: no-ignored-return and
        // different-types-comparison stop firing, and no-redundant-optional starts
        // reporting false positives. The type service is the price of the plugin.
        projectService: true,
        tsconfigRootDir: import.meta.dirname,
      },
    },
    plugins: {
      sonarjs,
      security,
      "simple-import-sort": simpleImportSort,
    },
    rules: {
      ...sonarjs.configs.recommended.rules,
      ...security.configs.recommended.rules,
      "simple-import-sort/imports": "error",
      "simple-import-sort/exports": "error",
      "no-restricted-syntax": [
        "error",
        {
          selector: "Literal[value=/[\\u2013\\u2014]/]",
          message:
            "No en or em dash in copy: use a plain -, or reword to a comma, colon or full stop.",
        },
        {
          selector: "TemplateElement[value.cooked=/[\\u2013\\u2014]/]",
          message:
            "No en or em dash in copy: use a plain -, or reword to a comma, colon or full stop.",
        },
        {
          selector: "JSXText[value=/[\\u2013\\u2014]/]",
          message:
            "No en or em dash in copy: use a plain -, or reword to a comma, colon or full stop.",
        },
        {
          selector: "Literal[regex.pattern=/[\\u2013\\u2014]/]",
          message:
            "No en or em dash in copy: use a plain -, or reword to a comma, colon or full stop.",
        },
      ],
    },
  },
  {
    files: ["**/*.test.{ts,tsx}", "src/test/**"],
    rules: {
      "sonarjs/no-duplicate-string": "off",
      // sonarjs 4.2 adds parameterized-tests; the suite deliberately favors explicit it() cases.
      "sonarjs/parameterized-tests": "off",
    },
  },
  prettier,
);
