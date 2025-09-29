;;for guile only

(import (scheme base)
        (scheme write)
        (prefix (web client) wclient/)
        (prefix (web response) wresp/))


;(define resp (wclient/http-get "https://www.google.com/"))
(define resp (wclient/http-request "https://www.google.com/"
                                   #:method 'GET))

(write (wresp/response-code resp))(newline);;200

