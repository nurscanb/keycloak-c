import {useTranslation} from "react-i18next";
import {useFormContext} from "react-hook-form";
import {BackendUrlField} from "../../component/turksat/BackendUrlField";
import IdentityProviderRepresentation from "@keycloak/keycloak-admin-client/lib/defs/identityProviderRepresentation";
import {FormGroup} from "@patternfly/react-core";
import {useEffect, useState} from "react";


type EsignGeneralSettingsProps = {
    // id: string;
    create?:boolean;
    urlRequired?:boolean;
};

export const EsignGeneralSettings = ({
                                         create=true,
                                         urlRequired=true,
                                     }: EsignGeneralSettingsProps) => {
    const {t} = useTranslation("identity-providers");
    const [esignUrl, setEsignUrl] = useState<string>('');
    const {
        formState:{errors},
    } = useFormContext();

    const handleUrlChange = (value: string) => {
        console.log('Esign Yeni de�er:', value);
        localStorage.setItem(esignUrl,value)

    };
    useEffect( () => {
        const storedValue = localStorage.getItem(esignUrl);
        if (storedValue) {
            try {
                setEsignUrl(storedValue);
                sendRequestToBackend(storedValue);
                console.log(`storedvalue funct..  ${storedValue}`)

            }catch (error){
                console.error('Ana fonksiyon �al��t�r�l�rken bir hata olu�tu:', error);
            }
        }else {
            console.error('LocalStorage\'dan de�er al�namad�.');
        }

    }, []);

    const sendRequestToBackend = async (dynamicUrl:string) => {
        try {
            const response = await fetch("http://localhost:8080/realms/master/login-actions/esignBackend", {
                method: 'POST',
                headers: {
                    'Content-Type': 'text/plain'
                },
                body: `${dynamicUrl}`
            });
            if (response.ok) {
                const data = await response.json();
                console.log("E-sign Backend'den gelen veri:", data);
            } else {
                console.error('Hatal� yan�t al�nd�:', response);
            }
        } catch (error) {
            console.error('Bir hata olu�tu:', error);
        }
    };
    return(
        <>
            <BackendUrlField id="esign" urlRequired={urlRequired} onValueChange={handleUrlChange}/>
        </>
    )
}
