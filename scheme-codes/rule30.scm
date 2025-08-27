(import (scheme base)
        (scheme write)
        (only (srfi 1) unfold))

(define (make-initial-state)
  (unfold (lambda (x) (= x 31))
          (lambda (x) (= x 15))
          (lambda (x) (+ x 1))
          0))

(define (update-state state)
  (unfold (lambda (x) (= x 31))
          (lambda (x)
            (let* ((left (if (zero? x) #f (list-ref state (- x 1))))
                   (right (if (= x 30) #f (list-ref state (+ x 1))))
                   (v (+ (if left 4 0)
                         (if (list-ref state x) 2 0)
                         (if right 1 0))))
              (case v
                ((#b111 #b110 #b101 #b000) #f)
                (else #t))))
          (lambda (x) (+ x 1))
          0))

(define (print-state state)
  (for-each (lambda (x) (display (if x "■" "□"))) state)
  (newline))

(for-each
  print-state
  (unfold (lambda (x) (= (car x) 10))
          cdr
          (lambda (x) (cons (+ (car x) 1)
                       (update-state (cdr x))))
         (cons 0 (make-initial-state))))
