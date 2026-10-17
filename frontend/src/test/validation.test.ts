import { describe, expect, it } from 'vitest';
import { validateEmail, validateName, validatePassword, validatePhone } from '../lib/validation';
import { formatMoney, parseRupeesToPaise } from '../lib/format';

describe('validation', () => {
  it('accepts valid Indian mobile numbers in common formats', () => {
    for (const p of ['9876543210', '+91 98765 43210', '09876543210', '98765-43210']) expect(validatePhone(p)).toBeNull();
  });
  it('rejects bad phones', () => {
    for (const p of ['', '12345', '5876543210', '98765432100']) expect(validatePhone(p)).not.toBeNull();
  });
  it('checks password rules the server enforces', () => {
    expect(validatePassword('short1')).not.toBeNull();
    expect(validatePassword('onlyletters')).not.toBeNull();
    expect(validatePassword('12345678')).not.toBeNull();
    expect(validatePassword('Gymlover99')).toBeNull();
  });
  it('validates email and name', () => {
    expect(validateEmail('')).toBeNull();
    expect(validateEmail('a@b.co')).toBeNull();
    expect(validateEmail('nope')).not.toBeNull();
    expect(validateName(' A ')).not.toBeNull();
    expect(validateName('Asha')).toBeNull();
  });
});

describe('money', () => {
  it('formats paise as rupees with Indian grouping', () => {
    expect(formatMoney(150000)).toBe('₹1,500');
    expect(formatMoney(150050)).toBe('₹1,500.50');
    expect(formatMoney(0)).toBe('₹0');
    expect(formatMoney(null)).toBe('—');
  });
  it('parses rupee input exactly, never via floating point', () => {
    expect(parseRupeesToPaise('1500')).toBe(150000);
    expect(parseRupeesToPaise('1,500.5')).toBe(150050);
    expect(parseRupeesToPaise('0.29')).toBe(29);
    expect(parseRupeesToPaise('1.234')).toBeNull();
    expect(parseRupeesToPaise('-5')).toBeNull();
    expect(parseRupeesToPaise('abc')).toBeNull();
  });
});
