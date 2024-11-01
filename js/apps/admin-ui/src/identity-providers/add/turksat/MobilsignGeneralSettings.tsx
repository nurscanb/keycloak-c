import { useTranslation } from "react-i18next";
import { useFormContext } from "react-hook-form";
import { BackendUrlField } from "../../component/turksat/BackendUrlField";
import { useEffect, useState } from "react";

type MobilsignGeneralSettingsProps = {
    // id: string;
    create?: boolean;
    urlRequired?: boolean;
};

export const MobilsignGeneralSettings = ({
                                             create=true,
                                             urlRequired = true,
                                         }: MobilsignGeneralSettingsProps) => {
    const { t} = useTranslation("identity-providers");
    const [mobilsignUrl, setMobilsignUrl] = useState<string>("");
    const {
        formState:{ errors},
    } = useFormContext();
    const handleUrlChange = (value:string) => {
        console.log('Mobilsign Yeni deðer:', value);
        localStorage.setItem(mobilsignUrl,value);
    };

    useEffect( () => {
        const storedValue = localStorage.getItem(mobilsignUrl);
        if (storedValue) {
            try {
                setMobilsignUrl(storedValue);
                sendRequestToBackend(storedValue);
                console.log(`storedvalue funct..  ${storedValue}`)

            }catch (error){
                console.error('Ana fonksiyon çalýþtýrýlýrken bir hata oluþtu:', error);
            }
        }else {
            console.error('LocalStorage\'dan deðer alýnamadý.');
        }

    }, []);

    const sendRequestToBackend = async (dynamicUrl:string) => {
        try {
            const response = await fetch("http://localhost:8080/realms/master/login-actions/mobilsignBackend", {
                method: 'POST',
                headers: {
                    'Content-Type': 'text/plain'
                },
                body: `${dynamicUrl}`
            });
            if (response.ok) {
                const data = await response.json();
                console.log("Mobilsign Backend'den gelen veri:", data);
            } else {
                console.error('Hatalý yanýt alýndý:', response);
            }
        } catch (error) {
            console.error('Bir hata oluþtu:', error);
        }
    };
    return(
        <>
            <BackendUrlField onValueChange={handleUrlChange} id="mobilsign" urlRequired={urlRequired}/>
        </>
    )
}
