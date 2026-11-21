INSERT INTO cliente(nome,email) VALUES
('Ana Costa','ana@example.test'),('Bruno Silva','bruno@example.test'),('Clara Lima','clara@example.test'),
('Diego Ramos','diego@example.test'),('Elisa Moura','elisa@example.test');
INSERT INTO corretor(nome,email,creci) VALUES
('Mariana Alves','mariana@example.test','SC-14001'),('Rafael Melo','rafael@example.test','SC-14002'),
('Tânia Rocha','tania@example.test','SC-14003');
INSERT INTO proprietario(nome,email) VALUES
('Cecília Andrade','cecilia@example.test'),('Paulo Reis','paulo@example.test'),('Eva Duarte','eva@example.test');
INSERT INTO imovel(proprietario_id,titulo,bairro,endereco,preco,criado_em,status) VALUES
(1,'Casa Jardim das Oliveiras','Centro','Rua das Oliveiras, 120',610000,CURRENT_DATE - 75,'DISPONIVEL'),
(2,'Sobrado Vista do Vale','Vila Nova','Rua do Vale, 42',780000,CURRENT_DATE - 72,'DISPONIVEL'),
(3,'Casa Pátio das Flores','Centro','Rua das Flores, 319',485000,CURRENT_DATE - 68,'DISPONIVEL'),
(1,'Casa Bosque Norte','Bela Vista','Rua do Bosque, 58',540000,CURRENT_DATE - 67,'DISPONIVEL'),
(2,'Casa da Praça','Vila Nova','Rua da Praça, 14',395000,CURRENT_DATE - 62,'DISPONIVEL'),
(3,'Sobrado do Mirante','Bela Vista','Rua do Mirante, 81',920000,CURRENT_DATE - 91,'VENDIDO');
INSERT INTO horario(corretor_id,inicio)
SELECT c.id, DATEADD('HOUR', h.x, DATEADD('DAY', d.x, CAST(CURRENT_DATE AS TIMESTAMP)))
FROM corretor c CROSS JOIN SYSTEM_RANGE(-52,14) d CROSS JOIN SYSTEM_RANGE(9,16) h
WHERE d.x <> 0 AND h.x IN (9,11,14,16);
INSERT INTO visita(cliente_id,imovel_id,horario_id,inicio,preco_registrado,status,ativo)
SELECT MOD(h.id,5)+1, i.id, h.id, h.inicio, i.preco,
CASE WHEN MOD(h.id,7)=0 THEN 'CANCELADA' WHEN h.inicio < CURRENT_DATE THEN 'REALIZADA' ELSE 'PENDENTE' END,
CASE WHEN MOD(h.id,7)=0 THEN NULL ELSE 1 END
FROM horario h JOIN imovel i ON i.id = CASE
    WHEN h.inicio < CURRENT_DATE - 35 AND MOD(h.id,2)=0 THEN 4
    WHEN MOD(h.id, 5) IN (0,1,2) THEN 1 WHEN MOD(h.id, 5)=3 THEN 2 ELSE 3 END
WHERE MOD(h.id,17)=0;
