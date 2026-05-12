"use client";

import { createContext, useContext, useEffect, useState, type ReactNode } from "react";

export type InterfaceLanguage = "ru" | "en";

type LanguageOption = {
  value: InterfaceLanguage;
  label: string;
};

type LanguageContextValue = {
  language: InterfaceLanguage;
  setLanguage: (language: InterfaceLanguage) => void;
};

const STORAGE_KEY = "t360lab.interface-language";

export const interfaceLanguageOptions: LanguageOption[] = [
  {
    value: "ru",
    label: "RUS",
  },
  {
    value: "en",
    label: "ENG",
  },
];

const LanguageContext = createContext<LanguageContextValue | null>(null);

function isInterfaceLanguage(value: string | null): value is InterfaceLanguage {
  return value === "ru" || value === "en";
}

export function LanguageProvider({ children }: { children: ReactNode }) {
  const [language, setLanguage] = useState<InterfaceLanguage>(() => {
    if (typeof window === "undefined") {
      return "ru";
    }

    const savedLanguage = window.localStorage.getItem(STORAGE_KEY);
    return isInterfaceLanguage(savedLanguage) ? savedLanguage : "ru";
  });

  useEffect(() => {
    document.documentElement.lang = language === "en" ? "en" : "ru";
    window.localStorage.setItem(STORAGE_KEY, language);
  }, [language]);

  return <LanguageContext.Provider value={{ language, setLanguage }}>{children}</LanguageContext.Provider>;
}

export function useLanguage() {
  const context = useContext(LanguageContext);

  if (!context) {
    throw new Error("useLanguage must be used within LanguageProvider");
  }

  return context;
}
