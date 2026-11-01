# PolskiAuth – Paper 1.21.4

Projekt źródłowy polskiego pluginu rejestracji i logowania. Wymaga Paper 1.21.4 i Java 21.

## Jak uzyskać plik JAR bez instalowania Maven na komputerze

1. Zaloguj się lub załóż konto na https://github.com/.
2. Utwórz nowe repozytorium (New repository), np. `PolskiAuth`.
3. Wgraj do repozytorium wszystkie pliki z tego folderu ZIP, razem z ukrytym folderem `.github`.
4. Otwórz zakładkę **Actions** w repozytorium.
5. Wybierz workflow **Build PolskiAuth** i kliknij **Run workflow** (jeśli nie uruchomił się automatycznie po wgraniu plików).
6. Po zakończeniu kompilacji otwórz wykonane zadanie i na dole w sekcji **Artifacts** pobierz `PolskiAuth-Paper-1.21.4.zip`.
7. Rozpakuj pobrane archiwum. W środku będzie `PolskiAuth-1.0.0.jar`.

## Instalacja na IceHost

1. Zatrzymaj serwer.
2. Wgraj `PolskiAuth-1.0.0.jar` do folderu `plugins`.
3. Uruchom serwer ponownie.
4. Sprawdź komendą `/plugins`, czy PolskiAuth jest na zielono.

## Komendy

- `/reg <hasło> <powtórz hasło>` – rejestracja (aliasy `/register`, `/zarejestruj`)
- `/l <hasło>` – logowanie (aliasy `/login`, `/zaloguj`)
- `/zmienhaslo <stare> <nowe>` – zmiana hasła (alias `/changepassword`)

Hasła są przechowywane jako skróty PBKDF2-HMAC-SHA256 z losową solą.

## Dodatkowe elementy

- tytuł ANARCHIA po wejściu;
- pasek BossBar z limitem 60 sekund;
- ostrzeżenia w ostatnich 10 sekundach;
- wyrzucenie niezalogowanego gracza po upływie czasu.

Przed użyciem na publicznym serwerze przetestuj plugin na kopii serwera.
