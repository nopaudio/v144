<?php
return [
    'db' => [
        'host' => 'localhost',
        'port' => 3306,
        'name' => 'database_name',
        'user' => 'database_user',
        'pass' => 'database_password',
    ],
    'app' => [
        'base_url' => 'https://example.com/khai-phraban',
        'app_key' => 'CHANGE_TO_RANDOM_SECRET',
        'timezone' => 'Asia/Bangkok',
        'upload_max_bytes' => 5242880,
        'max_images' => 5,
        'post_cooldown_seconds' => 30,
        // แนะนำให้ตั้งเป็น path นอก public_html สำหรับเอกสารยืนยันตัวตน
        // เว้นว่างไว้จะใช้ khaiphraban_private/ หนึ่งระดับเหนือ backend และสร้าง .htaccess ป้องกันซ้ำ
        'private_storage_path' => '',
    ],
];
