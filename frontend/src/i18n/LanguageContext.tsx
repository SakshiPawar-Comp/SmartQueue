import React, { createContext, useContext, useState, useEffect } from 'react';
import { Language, translations } from './translations';
import { useAuth } from '../context/AuthContext';

interface LanguageContextType {
  language: Language;
  setLanguage: (lang: Language) => void;
  t: (key: string, params?: Record<string, string | number>) => string;
}

const LanguageContext = createContext<LanguageContextType | undefined>(undefined);

export const LanguageProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { user } = useAuth();
  const [language, setLanguageState] = useState<Language>(() => {
    const saved = localStorage.getItem('smartqueue_lang') as Language;
    if (saved === 'en' || saved === 'hi' || saved === 'mr') {
      return saved;
    }
    return 'en';
  });

  useEffect(() => {
    if (user?.preferredLanguage && (user.preferredLanguage === 'en' || user.preferredLanguage === 'hi' || user.preferredLanguage === 'mr')) {
      setLanguageState(user.preferredLanguage as Language);
      localStorage.setItem('smartqueue_lang', user.preferredLanguage);
    }
  }, [user?.preferredLanguage]);

  const setLanguage = (lang: Language) => {
    setLanguageState(lang);
    localStorage.setItem('smartqueue_lang', lang);
  };

  const t = (key: string, params?: Record<string, string | number>): string => {
    const dict = translations[language] || translations.en;
    let translation = dict[key] || translations.en[key] || key;
    if (params) {
      Object.entries(params).forEach(([paramKey, paramVal]) => {
        translation = translation.replace(new RegExp(`\\{${paramKey}\\}`, 'g'), String(paramVal));
      });
    }
    return translation;
  };

  return (
    <LanguageContext.Provider value={{ language, setLanguage, t }}>
      {children}
    </LanguageContext.Provider>
  );
};

export const useTranslation = () => {
  const context = useContext(LanguageContext);
  if (!context) {
    throw new Error('useTranslation must be used within a LanguageProvider');
  }
  return context;
};

export const useLanguage = useTranslation;

